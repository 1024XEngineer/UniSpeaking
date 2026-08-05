package com.unispeaking.common.document;

import com.unispeaking.common.exception.document.DocumentErrorCode;
import com.unispeaking.common.exception.document.DocumentException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * 安全提取文本型 PDF 与 DOCX 的统一入口。
 */
public final class DocumentTextExtractor {

	static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
	static final int MAX_PDF_PAGES = 10;
	static final int MAX_TEXT_CHARS = 20_000;
	static final int MAX_ZIP_ENTRIES = 1_000;
	static final int MAX_ZIP_ENTRY_BYTES = MAX_FILE_BYTES;
	static final int MAX_ZIP_UNCOMPRESSED_BYTES = MAX_FILE_BYTES;

	private static final String PDF_MIME_TYPE = "application/pdf";
	private static final String DOCX_MIME_TYPE =
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document";
	private static final byte[] PDF_SIGNATURE = new byte[] {'%', 'P', 'D', 'F', '-'};
	private static final byte[] ZIP_LOCAL_FILE_SIGNATURE = new byte[] {'P', 'K', 3, 4};

	private final PdfTextParser pdfTextParser;
	private final DocxTextParser docxTextParser;

	public DocumentTextExtractor() {
		this(new PdfTextParser(), new DocxTextParser());
	}

	private DocumentTextExtractor(PdfTextParser pdfTextParser, DocxTextParser docxTextParser) {
		this.pdfTextParser = Objects.requireNonNull(pdfTextParser, "pdfTextParser must not be null");
		this.docxTextParser = Objects.requireNonNull(docxTextParser, "docxTextParser must not be null");
	}

	public String extractText(String filename, String mimeType, byte[] content) {
		validateInput(filename, mimeType, content);
		DocumentType documentType = detectType(filename, mimeType, content);
		String extracted = switch (documentType) {
			case PDF -> pdfTextParser.parse(content);
			case DOCX -> docxTextParser.parse(content);
		};
		String normalized = normalizeText(extracted);
		if (normalized.isEmpty()) {
			throw new DocumentException(DocumentErrorCode.TEXT_EMPTY);
		}
		if (normalized.codePointCount(0, normalized.length()) > MAX_TEXT_CHARS) {
			throw new DocumentException(DocumentErrorCode.TEXT_TOO_LARGE);
		}
		return normalized;
	}

	private static void validateInput(String filename, String mimeType, byte[] content) {
		if (isBlank(filename) || isBlank(mimeType) || content == null || content.length == 0) {
			throw new DocumentException(DocumentErrorCode.INPUT_REQUIRED);
		}
		if (content.length > MAX_FILE_BYTES) {
			throw new DocumentException(DocumentErrorCode.TOO_LARGE);
		}
	}

	private static DocumentType detectType(String filename, String mimeType, byte[] content) {
		String normalizedName = filename.trim().toLowerCase(Locale.ROOT);
		String normalizedMimeType = normalizeMimeType(mimeType);
		if (normalizedName.endsWith(".pdf")
				&& PDF_MIME_TYPE.equals(normalizedMimeType)
				&& startsWith(content, PDF_SIGNATURE)) {
			return DocumentType.PDF;
		}
		if (normalizedName.endsWith(".docx")
				&& DOCX_MIME_TYPE.equals(normalizedMimeType)
				&& startsWith(content, ZIP_LOCAL_FILE_SIGNATURE)) {
			return DocumentType.DOCX;
		}
		throw new DocumentException(DocumentErrorCode.FORMAT_UNSUPPORTED);
	}

	private static String normalizeMimeType(String mimeType) {
		int parametersStart = mimeType.indexOf(';');
		String value = parametersStart >= 0 ? mimeType.substring(0, parametersStart) : mimeType;
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean startsWith(byte[] content, byte[] prefix) {
		if (content.length < prefix.length) {
			return false;
		}
		for (int index = 0; index < prefix.length; index++) {
			if (content[index] != prefix[index]) {
				return false;
			}
		}
		return true;
	}

	private static String normalizeText(String text) {
		if (text == null) {
			return "";
		}
		return text.replace("\r\n", "\n").replace('\r', '\n').strip();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private enum DocumentType {
		PDF,
		DOCX
	}

	private static final class PdfTextParser {

		String parse(byte[] content) {
			try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(content))) {
				if (document.getNumberOfPages() > MAX_PDF_PAGES) {
					throw new DocumentException(DocumentErrorCode.PDF_PAGE_LIMIT_EXCEEDED);
				}
				return new PDFTextStripper().getText(document);
			} catch (DocumentException exception) {
				throw exception;
			} catch (IOException exception) {
				throw new DocumentException(DocumentErrorCode.CONTENT_INVALID, exception);
			}
		}
	}

	private static final class DocxTextParser {

		String parse(byte[] content) {
			validateZipSafety(content);
			try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
					XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
				return extractor.getText();
			} catch (IOException | RuntimeException exception) {
				throw new DocumentException(DocumentErrorCode.CONTENT_INVALID, exception);
			}
		}

		private static void validateZipSafety(byte[] content) {
			int entryCount = 0;
			long totalUncompressedBytes = 0;
			byte[] buffer = new byte[8_192];
			try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
				ZipEntry entry;
				while ((entry = zip.getNextEntry()) != null) {
					if (entry.isDirectory()) {
						continue;
					}
					entryCount++;
					if (entryCount > MAX_ZIP_ENTRIES) {
						throw new DocumentException(DocumentErrorCode.CONTENT_INVALID);
					}
					long entryBytes = 0;
					int read;
					while ((read = zip.read(buffer)) != -1) {
						entryBytes += read;
						totalUncompressedBytes += read;
						if (entryBytes > MAX_ZIP_ENTRY_BYTES
								|| totalUncompressedBytes > MAX_ZIP_UNCOMPRESSED_BYTES) {
							throw new DocumentException(DocumentErrorCode.CONTENT_INVALID);
						}
					}
					zip.closeEntry();
				}
			} catch (DocumentException exception) {
				throw exception;
			} catch (ZipException exception) {
				throw new DocumentException(DocumentErrorCode.CONTENT_INVALID, exception);
			} catch (IOException exception) {
				throw new DocumentException(DocumentErrorCode.CONTENT_INVALID, exception);
			}
			if (entryCount == 0) {
				throw new DocumentException(DocumentErrorCode.CONTENT_INVALID);
			}
		}
	}
}
