package com.unispeaking.component.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.document.DocumentTextExtractor;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.common.exception.document.DocumentErrorCode;
import com.unispeaking.common.exception.document.DocumentException;
import com.unispeaking.common.exception.ocr.OcrErrorCode;
import com.unispeaking.common.exception.ocr.OcrException;
import com.unispeaking.domain.dto.ocr.OcrImage;
import com.unispeaking.domain.dto.scene.InterviewMaterialPreparationInput;
import com.unispeaking.domain.dto.scene.InterviewResumeFile;
import com.unispeaking.provider.OcrProvider;
import org.junit.jupiter.api.Test;

class MaterialTextExtractionTest {

	private static final String PDF_MIME_TYPE = "application/pdf";
	private static final String DOCX_MIME_TYPE =
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document";

	private final OcrProvider ocrProvider = mock(OcrProvider.class);
	private final DocumentTextExtractor documentTextExtractor =
			mock(DocumentTextExtractor.class);
	private final MaterialTextExtraction extraction = new MaterialTextExtraction(
			ocrProvider,
			documentTextExtractor);

	@Test
	void returnsJobDescriptionTextWithAbsentResume() {
		MaterialTextExtraction.MaterialTextResult result = extraction.extract(
				textInput("JD 文本", null, null, null));

		assertEquals("JD 文本", result.jobDescriptionText());
		assertNull(result.resumeText());
		assertTrue(result.resumeAbsent());
	}

	@Test
	void returnsResumeTextWhenProvided() {
		MaterialTextExtraction.MaterialTextResult result = extraction.extract(
				textInput("JD 文本", "简历文本", null, null));

		assertEquals("JD 文本", result.jobDescriptionText());
		assertEquals("简历文本", result.resumeText());
		assertFalse(result.resumeAbsent());
	}

	@Test
	void recognizesJobDescriptionImageThroughOcr() {
		when(ocrProvider.available()).thenReturn(true);
		when(ocrProvider.recognizeText(any())).thenReturn("JD OCR 文本");
		byte[] image = new byte[] {(byte) 0x89, 'P', 'N', 'G'};

		MaterialTextExtraction.MaterialTextResult result = extraction.extract(
				new InterviewMaterialPreparationInput(
						null,
						null,
						null,
						new OcrImage(image)));

		assertEquals("JD OCR 文本", result.jobDescriptionText());
		verify(ocrProvider).recognizeText(any());
	}

	@Test
	void failsWithOcrUnavailableWhenOcrNotConfigured() {
		when(ocrProvider.available()).thenReturn(false);

		OcrException exception = assertThrows(
				OcrException.class,
				() -> extraction.extract(
						new InterviewMaterialPreparationInput(
								null,
								null,
								null,
								new OcrImage(new byte[] {1}))));

		assertSame(OcrErrorCode.UNAVAILABLE, exception.errorCode());
		verify(ocrProvider, never()).recognizeText(any());
	}

	@Test
	void rejectsJobDescriptionTextAndImageTogether() {
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> extraction.extract(
						new InterviewMaterialPreparationInput(
								null,
								null,
								"JD 文本",
								new OcrImage(new byte[] {1}))));

		assertEquals(InterviewErrorCode.INTERVIEW_REQUEST_INVALID, exception.code());
	}

	@Test
	void rejectsMissingJobDescription() {
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> extraction.extract(
						new InterviewMaterialPreparationInput(null, null, null, null)));

		assertEquals(InterviewErrorCode.INTERVIEW_REQUEST_INVALID, exception.code());
	}

	@Test
	void rejectsResumeTextAndFileTogether() {
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> extraction.extract(
						new InterviewMaterialPreparationInput(
								"简历文本",
								new InterviewResumeFile("resume.pdf", PDF_MIME_TYPE, new byte[] {1}),
								"JD 文本",
								null)));

		assertEquals(InterviewErrorCode.INTERVIEW_REQUEST_INVALID, exception.code());
	}

	@Test
	void extractsResumePdfText() {
		when(documentTextExtractor.extractText(
				anyString(), anyString(), any(byte[].class)))
				.thenReturn("PDF 抽取文本");
		byte[] content = new byte[] {'%', 'P', 'D', 'F', '-'};

		MaterialTextExtraction.MaterialTextResult result = extraction.extract(
				new InterviewMaterialPreparationInput(
						null,
						new InterviewResumeFile("resume.pdf", PDF_MIME_TYPE, content),
						"JD 文本",
						null));

		assertEquals("PDF 抽取文本", result.resumeText());
		assertFalse(result.resumeAbsent());
		verify(documentTextExtractor).extractText(
				eq("resume.pdf"), eq(PDF_MIME_TYPE), any(byte[].class));
	}

	@Test
	void extractsResumeDocxText() {
		when(documentTextExtractor.extractText(
				anyString(), anyString(), any(byte[].class)))
				.thenReturn("DOCX 抽取文本");
		byte[] content = new byte[] {'P', 'K', 3, 4};

		MaterialTextExtraction.MaterialTextResult result = extraction.extract(
				new InterviewMaterialPreparationInput(
						null,
						new InterviewResumeFile("resume.docx", DOCX_MIME_TYPE, content),
						"JD 文本",
						null));

		assertEquals("DOCX 抽取文本", result.resumeText());
		verify(documentTextExtractor).extractText(
				eq("resume.docx"), eq(DOCX_MIME_TYPE), any(byte[].class));
	}

	@Test
	void rejectsLegacyDocResumeWithFormatUnsupported() {
		byte[] content = new byte[] {(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0};

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> extraction.extract(
						new InterviewMaterialPreparationInput(
								null,
								new InterviewResumeFile("resume.doc", "application/msword", content),
								"JD 文本",
								null)));

		assertEquals(DocumentErrorCode.FORMAT_UNSUPPORTED.code(), exception.code());
		verify(documentTextExtractor, never()).extractText(
				anyString(), anyString(), any(byte[].class));
	}

	@Test
	void rejectsImageResumeWithFormatUnsupported() {
		byte[] content = new byte[] {(byte) 0x89, 'P', 'N', 'G'};

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> extraction.extract(
						new InterviewMaterialPreparationInput(
								null,
								new InterviewResumeFile("resume.png", "image/png", content),
								"JD 文本",
								null)));

		assertEquals(DocumentErrorCode.FORMAT_UNSUPPORTED.code(), exception.code());
		verify(documentTextExtractor, never()).extractText(
				anyString(), anyString(), any(byte[].class));
	}

	@Test
	void propagatesScannedPdfTextEmpty() {
		when(documentTextExtractor.extractText(
				anyString(), anyString(), any(byte[].class)))
				.thenThrow(new DocumentException(DocumentErrorCode.TEXT_EMPTY));

		DocumentException exception = assertThrows(
				DocumentException.class,
				() -> extraction.extract(
						new InterviewMaterialPreparationInput(
								null,
								new InterviewResumeFile("resume.pdf", PDF_MIME_TYPE, new byte[] {1}),
								"JD 文本",
								null)));

		assertSame(DocumentErrorCode.TEXT_EMPTY, exception.errorCode());
	}

	@Test
	void validatesDependenciesNullInputAndTextLengthBounds() {
		assertThrows(NullPointerException.class,
				() -> new MaterialTextExtraction(null, documentTextExtractor));
		assertThrows(NullPointerException.class,
				() -> new MaterialTextExtraction(ocrProvider, null));
		assertThrows(BusinessException.class, () -> extraction.extract(null));
		String oversized = "x".repeat(20_001);
		assertThrows(BusinessException.class,
				() -> extraction.extract(textInput(oversized, null, null, null)));
		assertThrows(BusinessException.class,
				() -> extraction.extract(textInput("JD", oversized, null, null)));
	}

	@Test
	void treatsEmptyImageAndResumeFileContentAsAbsent() {
		var result = extraction.extract(new InterviewMaterialPreparationInput(
				null, new InterviewResumeFile("resume.pdf", PDF_MIME_TYPE, new byte[0]),
				"JD", new OcrImage(new byte[0])));

		assertEquals("JD", result.jobDescriptionText());
		assertTrue(result.resumeAbsent());
	}

	@Test
	void rejectsNullAndBlankOcrResults() {
		when(ocrProvider.available()).thenReturn(true);
		when(ocrProvider.recognizeText(any())).thenReturn(null, " ");
		var input = new InterviewMaterialPreparationInput(
				null, null, null, new OcrImage(new byte[] {1}));

		assertThrows(BusinessException.class, () -> extraction.extract(input));
		assertThrows(BusinessException.class, () -> extraction.extract(input));
	}

	@Test
	void detectsLegacyDocCaseAndEveryImageExtensionOrParameterizedMime() {
		assertUnsupported(new InterviewResumeFile(" RESUME.DOC ", null, new byte[] {1}));
		assertUnsupported(new InterviewResumeFile(null, "IMAGE/PNG; charset=binary", new byte[] {1}));
		for (String extension : new String[] {"jpg", "jpeg", "gif", "bmp", "webp"}) {
			assertUnsupported(new InterviewResumeFile("resume." + extension, null, new byte[] {1}));
		}
	}

	private void assertUnsupported(InterviewResumeFile file) {
		BusinessException exception = assertThrows(BusinessException.class,
				() -> extraction.extract(textInput("JD", null, file, null)));
		assertEquals(DocumentErrorCode.FORMAT_UNSUPPORTED.code(), exception.code());
	}

	private static InterviewMaterialPreparationInput textInput(
			String jobDescriptionText,
			String resumeText,
			InterviewResumeFile resumeFile,
			OcrImage jobDescriptionImage) {
		return new InterviewMaterialPreparationInput(
				resumeText,
				resumeFile,
				jobDescriptionText,
				jobDescriptionImage);
	}
}
