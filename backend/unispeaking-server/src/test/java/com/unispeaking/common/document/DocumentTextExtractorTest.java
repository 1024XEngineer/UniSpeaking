package com.unispeaking.common.document;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.common.exception.document.DocumentErrorCode;
import com.unispeaking.common.exception.document.DocumentException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class DocumentTextExtractorTest {

	private static final String PDF_MIME_TYPE = "application/pdf";
	private static final String DOCX_MIME_TYPE =
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document";

	private final DocumentTextExtractor extractor = new DocumentTextExtractor();

	@Test
	void extractsMinimalTextPdfAndNormalizesLineEndings() {
		byte[] pdf = pdfWithText("  hello\r\npdf  ");

		String text = extractor.extractText("resume.pdf", PDF_MIME_TYPE, pdf);

		assertEquals("hello\npdf", text);
	}

	@Test
	void extractsMinimalDocxText() {
		byte[] docx = docxWithParagraphs("  hello docx  ");

		String text = extractor.extractText("resume.docx", DOCX_MIME_TYPE, docx);

		assertEquals("hello docx", text);
	}

	@Test
	void extractsDocxTableContent() {
		byte[] docx = docxWithTable("Company", "UniSpeaking", "Role", "Coach");

		String text = extractor.extractText("resume.docx", DOCX_MIME_TYPE, docx);

		assertAll(
				() -> assertTrue(text.contains("Company")),
				() -> assertTrue(text.contains("UniSpeaking")),
				() -> assertTrue(text.contains("Role")),
				() -> assertTrue(text.contains("Coach")));
	}

	@Test
	void rejectsPdfAbovePageLimit() {
		byte[] pdf = pdfWithPages(DocumentTextExtractor.MAX_PDF_PAGES + 1);

		assertError(
				"resume.pdf",
				PDF_MIME_TYPE,
				pdf,
				DocumentErrorCode.PDF_PAGE_LIMIT_EXCEEDED);
	}

	@Test
	void acceptsPdfAtPageLimit() {
		byte[] pdf = pdfWithTextPages(DocumentTextExtractor.MAX_PDF_PAGES, "page text");

		String text = extractor.extractText("resume.pdf", PDF_MIME_TYPE, pdf);

		assertTrue(text.contains("page text"));
	}

	@Test
	void rejectsEmptyTextPdfAsScannedDocument() {
		byte[] pdf = pdfWithPages(1);

		assertError(
				"resume.pdf",
				PDF_MIME_TYPE,
				pdf,
				DocumentErrorCode.TEXT_EMPTY);
	}

	@Test
	void rejectsZipBombBeforeDocxParsing() {
		byte[] zipBomb = zipWithEntry(
				"word/document.xml",
				new byte[DocumentTextExtractor.MAX_ZIP_UNCOMPRESSED_BYTES + 1]);

		assertError(
				"resume.docx",
				DOCX_MIME_TYPE,
				zipBomb,
				DocumentErrorCode.CONTENT_INVALID);
	}

	@Test
	void acceptsExtractedTextAtMaximumLengthAndRejectsOneCharacterMore() {
		byte[] maximum = docxWithParagraphs("a".repeat(DocumentTextExtractor.MAX_TEXT_CHARS));
		byte[] overMaximum = docxWithParagraphs("a".repeat(DocumentTextExtractor.MAX_TEXT_CHARS + 1));

		assertAll(
				() -> assertEquals(
						DocumentTextExtractor.MAX_TEXT_CHARS,
						extractor.extractText("resume.docx", DOCX_MIME_TYPE, maximum).length()),
				() -> assertError(
						"resume.docx",
						DOCX_MIME_TYPE,
						overMaximum,
						DocumentErrorCode.TEXT_TOO_LARGE));
	}

	@Test
	void rejectsOversizedFiles() {
		byte[] content = new byte[DocumentTextExtractor.MAX_FILE_BYTES + 1];
		Arrays.fill(content, (byte) 'a');

		assertError(
				"resume.pdf",
				PDF_MIME_TYPE,
				content,
				DocumentErrorCode.TOO_LARGE);
	}

	@Test
	void rejectsUnsupportedExtensionMimeAndSignatureCombinations() {
		byte[] pdf = pdfWithText("hello");
		byte[] docx = docxWithParagraphs("hello");
		byte[] image = new byte[] {
				(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'
		};

		assertAll(
				() -> assertError(
						"resume.doc",
						"application/msword",
						new byte[] {(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0},
						DocumentErrorCode.FORMAT_UNSUPPORTED),
				() -> assertError(
						"resume.md",
						"text/markdown",
						"# hello".getBytes(StandardCharsets.UTF_8),
						DocumentErrorCode.FORMAT_UNSUPPORTED),
				() -> assertError(
						"resume.docm",
						"application/vnd.ms-word.document.macroEnabled.12",
						docx,
						DocumentErrorCode.FORMAT_UNSUPPORTED),
				() -> assertError(
						"resume.png",
						"image/png",
						image,
						DocumentErrorCode.FORMAT_UNSUPPORTED),
				() -> assertError(
						"resume.pdf",
						DOCX_MIME_TYPE,
						pdf,
						DocumentErrorCode.FORMAT_UNSUPPORTED),
				() -> assertError(
						"resume.docx",
						DOCX_MIME_TYPE,
						pdf,
						DocumentErrorCode.FORMAT_UNSUPPORTED));
	}

	@Test
	void rejectsCorruptZipContainers() {
		byte[] corruptZip = new byte[] {'P', 'K', 3, 4, 1, 2, 3};

		assertError(
				"resume.docx",
				DOCX_MIME_TYPE,
				corruptZip,
				DocumentErrorCode.CONTENT_INVALID);
	}

	@Test
	void rejectsMissingInput() {
		assertAll(
				() -> assertError(
						null,
						DOCX_MIME_TYPE,
						new byte[] {1},
						DocumentErrorCode.INPUT_REQUIRED),
				() -> assertError(
						"resume.docx",
						null,
						new byte[] {1},
						DocumentErrorCode.INPUT_REQUIRED),
				() -> assertError(
						"resume.docx",
						DOCX_MIME_TYPE,
						null,
						DocumentErrorCode.INPUT_REQUIRED),
				() -> assertError(
						"resume.docx",
						DOCX_MIME_TYPE,
						new byte[0],
						DocumentErrorCode.INPUT_REQUIRED));
	}

	private void assertError(
			String filename,
			String mimeType,
			byte[] content,
			DocumentErrorCode expected) {
		DocumentException exception = assertThrows(
				DocumentException.class,
				() -> extractor.extractText(filename, mimeType, content));

		assertAll(
				() -> assertSame(expected, exception.errorCode()),
				() -> assertEquals(expected.code(), exception.code()),
				() -> assertEquals(expected.defaultMessage(), exception.getMessage()));
	}

	private static byte[] pdfWithText(String text) {
		try (PDDocument document = new PDDocument();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDPage page = new PDPage();
			document.addPage(page);
			writePageText(document, page, text);
			document.save(out);
			return out.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static byte[] pdfWithTextPages(int pages, String text) {
		try (PDDocument document = new PDDocument();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (int index = 0; index < pages; index++) {
				PDPage page = new PDPage();
				document.addPage(page);
				writePageText(document, page, text);
			}
			document.save(out);
			return out.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static void writePageText(PDDocument document, PDPage page, String text)
			throws IOException {
		try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
			contentStream.beginText();
			contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
			contentStream.newLineAtOffset(72, 720);
			for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
				contentStream.showText(line);
				contentStream.newLineAtOffset(0, -14);
			}
			contentStream.endText();
		}
	}

	private static byte[] pdfWithPages(int pages) {
		try (PDDocument document = new PDDocument();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (int index = 0; index < pages; index++) {
				document.addPage(new PDPage());
			}
			document.save(out);
			return out.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static byte[] docxWithParagraphs(String text) {
		try (XWPFDocument document = new XWPFDocument();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			document.createParagraph().createRun().setText(text);
			document.write(out);
			return out.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static byte[] docxWithTable(
			String firstHeader,
			String firstValue,
			String secondHeader,
			String secondValue) {
		try (XWPFDocument document = new XWPFDocument();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			XWPFTable table = document.createTable(2, 2);
			table.getRow(0).getCell(0).setText(firstHeader);
			table.getRow(0).getCell(1).setText(firstValue);
			table.getRow(1).getCell(0).setText(secondHeader);
			table.getRow(1).getCell(1).setText(secondValue);
			document.write(out);
			return out.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static byte[] zipWithEntry(String name, byte[] content) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
				ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry(name));
			zip.write(content);
			zip.closeEntry();
			zip.finish();
			return out.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
