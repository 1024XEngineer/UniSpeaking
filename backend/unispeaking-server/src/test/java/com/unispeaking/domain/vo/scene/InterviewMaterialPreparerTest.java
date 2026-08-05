package com.unispeaking.domain.vo.scene;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.document.DocumentTextExtractor;
import com.unispeaking.common.exception.interview.InterviewErrorCode;
import com.unispeaking.common.exception.interview.InterviewException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class InterviewMaterialPreparerTest {

	private static final String DOCX_MIME_TYPE =
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document";
	private static final String SUPPLEMENTARY_CODE_POINT = "\uD83D\uDE80";

	private final InterviewMaterialPreparer preparer =
			new InterviewMaterialPreparer(new DocumentTextExtractor());

	@Test
	void requiresJobTitleAndAllowsBothOptionalResumeSourcesToBeAbsent() {
		InterviewPreparedMaterials prepared = preparer.prepare(
				"  Backend Engineer  ",
				"  Build APIs\r\nSafely  ",
				"  ",
				null);

		assertAll(
				() -> assertEquals("Backend Engineer", prepared.jobTitle()),
				() -> assertEquals("Build APIs\nSafely", prepared.jobDescription()),
				() -> assertNull(prepared.resumeText()),
				() -> assertError(
						InterviewErrorCode.INPUT_INVALID,
						() -> preparer.prepare(" ", null, null, null)),
				() -> assertError(
						InterviewErrorCode.INPUT_INVALID,
						() -> preparer.prepare("\u2003", null, null, null)));
	}

	@Test
	void acceptsFinalTextLimitsAndRejectsOneCharacterMore() {
		InterviewPreparedMaterials prepared = preparer.prepare(
				"Engineer",
				"j".repeat(InterviewMaterialPreparer.MAX_JOB_DESCRIPTION_CODE_POINTS),
				"r".repeat(InterviewMaterialPreparer.MAX_RESUME_TEXT_CODE_POINTS),
				null);

		assertAll(
				() -> assertEquals(
						InterviewMaterialPreparer.MAX_JOB_DESCRIPTION_CODE_POINTS,
						prepared.jobDescription().length()),
				() -> assertEquals(
						InterviewMaterialPreparer.MAX_RESUME_TEXT_CODE_POINTS,
						prepared.resumeText().length()),
				() -> assertError(
						InterviewErrorCode.INPUT_INVALID,
						() -> preparer.prepare(
								"Engineer",
								"j".repeat(
										InterviewMaterialPreparer.MAX_JOB_DESCRIPTION_CODE_POINTS + 1),
								null,
								null)),
				() -> assertError(
						InterviewErrorCode.INPUT_INVALID,
						() -> preparer.prepare(
								"Engineer",
								null,
								"r".repeat(
										InterviewMaterialPreparer.MAX_RESUME_TEXT_CODE_POINTS + 1),
								null)));
	}

	@Test
	void countsJobDescriptionAndResumeByUnicodeCodePoint() {
		String maximumJobDescription = SUPPLEMENTARY_CODE_POINT.repeat(
				InterviewMaterialPreparer.MAX_JOB_DESCRIPTION_CODE_POINTS);
		String maximumResume = SUPPLEMENTARY_CODE_POINT.repeat(
				InterviewMaterialPreparer.MAX_RESUME_TEXT_CODE_POINTS);

		InterviewPreparedMaterials prepared = preparer.prepare(
				"Engineer",
				maximumJobDescription,
				maximumResume,
				null);

		assertAll(
				() -> assertEquals(
						InterviewMaterialPreparer.MAX_JOB_DESCRIPTION_CODE_POINTS,
						prepared.jobDescription().codePointCount(
								0, prepared.jobDescription().length())),
				() -> assertEquals(
						InterviewMaterialPreparer.MAX_RESUME_TEXT_CODE_POINTS,
						prepared.resumeText().codePointCount(0, prepared.resumeText().length())),
				() -> assertError(
						InterviewErrorCode.INPUT_INVALID,
						() -> preparer.prepare(
								"Engineer",
								maximumJobDescription + SUPPLEMENTARY_CODE_POINT,
								null,
								null)),
				() -> assertError(
						InterviewErrorCode.INPUT_INVALID,
						() -> preparer.prepare(
								"Engineer",
								null,
								maximumResume + SUPPLEMENTARY_CODE_POINT,
								null)));
	}

	@Test
	void acceptsSupplementaryResumeAtFileTextLimit() {
		String maximumResume = SUPPLEMENTARY_CODE_POINT.repeat(
				InterviewMaterialPreparer.MAX_RESUME_TEXT_CODE_POINTS);

		InterviewPreparedMaterials prepared = preparer.prepare(
				"Engineer",
				null,
				null,
				new InterviewResumeFile(
						"resume.docx",
						DOCX_MIME_TYPE,
						docx(maximumResume)));

		assertEquals(
				InterviewMaterialPreparer.MAX_RESUME_TEXT_CODE_POINTS,
				prepared.resumeText().codePointCount(0, prepared.resumeText().length()));
	}

	@Test
	void rejectsResumeTextAndFileTogether() {
		InterviewResumeFile file = new InterviewResumeFile(
				"resume.docx",
				DOCX_MIME_TYPE,
				docx("file resume"));

		assertError(
				InterviewErrorCode.INPUT_INVALID,
				() -> preparer.prepare("Engineer", null, "text resume", file));
	}

	@Test
	void extractsDocxAndMapsUnsupportedFilesToInterviewError() {
		InterviewPreparedMaterials prepared = preparer.prepare(
				"Engineer",
				null,
				null,
				new InterviewResumeFile("resume.docx", DOCX_MIME_TYPE, docx("Java developer")));

		assertAll(
				() -> assertEquals("Java developer", prepared.resumeText()),
				() -> assertError(
						InterviewErrorCode.MEDIA_TYPE_UNSUPPORTED,
						() -> preparer.prepare(
								"Engineer",
								null,
								null,
								new InterviewResumeFile(
										"resume.doc",
										"application/msword",
										new byte[] {1, 2, 3}))));
	}

	@Test
	void keepsResumeBytesDefensiveAndRedactsMaterialText() {
		byte[] content = new byte[] {1, 2, 3};
		InterviewResumeFile file = new InterviewResumeFile("resume.pdf", "application/pdf", content);
		content[0] = 9;
		byte[] returned = file.content();
		returned[1] = 9;
		InterviewPreparedMaterials prepared = preparer.prepare(
				"Secret role",
				"Secret JD",
				"Secret resume",
				null);

		assertAll(
				() -> assertEquals(1, file.content()[0]),
				() -> assertEquals(2, file.content()[1]),
				() -> assertFalse(prepared.toString().contains("Secret")),
				() -> assertFalse(file.toString().contains("resume.pdf")),
				() -> assertFalse(file.toString().contains("[B@")));
	}

	private static byte[] docx(String text) {
		try (XWPFDocument document = new XWPFDocument();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.createParagraph().createRun().setText(text);
			document.write(output);
			return output.toByteArray();
		}
		catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static void assertError(
			InterviewErrorCode expected,
			org.junit.jupiter.api.function.Executable action) {
		InterviewException exception = assertThrows(InterviewException.class, action);
		assertSame(expected, exception.errorCode());
	}
}
