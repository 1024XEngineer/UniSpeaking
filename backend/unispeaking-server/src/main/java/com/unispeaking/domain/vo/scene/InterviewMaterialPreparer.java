package com.unispeaking.domain.vo.scene;

import com.unispeaking.common.document.DocumentTextExtractor;
import com.unispeaking.common.exception.document.DocumentException;
import com.unispeaking.common.exception.interview.InterviewErrorCode;
import com.unispeaking.common.exception.interview.InterviewException;
import com.unispeaking.common.exception.interview.InterviewInputExceptionMapper;
import java.util.Objects;

/**
 * Validates and normalizes transient Interview creation materials.
 */
public final class InterviewMaterialPreparer {

	static final int MAX_JOB_TITLE_CODE_POINTS = 255;
	static final int MAX_JOB_DESCRIPTION_CODE_POINTS = 5_000;
	static final int MAX_RESUME_TEXT_CODE_POINTS = 20_000;

	private final DocumentTextExtractor documentTextExtractor;

	public InterviewMaterialPreparer(DocumentTextExtractor documentTextExtractor) {
		this.documentTextExtractor = Objects.requireNonNull(
				documentTextExtractor,
				"documentTextExtractor must not be null");
	}

	public InterviewPreparedMaterials prepare(
			String jobTitle,
			String jobDescription,
			String resumeText,
			InterviewResumeFile resumeFile) {
		String normalizedJobTitle = normalizeOptional(jobTitle);
		if (normalizedJobTitle == null) {
			throw new InterviewException(InterviewErrorCode.INPUT_INVALID);
		}
		validateCodePointLength(normalizedJobTitle, MAX_JOB_TITLE_CODE_POINTS);

		String normalizedJobDescription = normalizeOptional(jobDescription);
		validateCodePointLength(
				normalizedJobDescription,
				MAX_JOB_DESCRIPTION_CODE_POINTS);

		String normalizedResumeText = normalizeOptional(resumeText);
		if (normalizedResumeText != null && resumeFile != null) {
			throw new InterviewException(InterviewErrorCode.INPUT_INVALID);
		}
		if (normalizedResumeText != null) {
			validateCodePointLength(normalizedResumeText, MAX_RESUME_TEXT_CODE_POINTS);
		}
		else if (resumeFile != null) {
			normalizedResumeText = extractResumeText(resumeFile);
		}

		return new InterviewPreparedMaterials(
				normalizedJobTitle,
				normalizedJobDescription,
				normalizedResumeText);
	}

	private String extractResumeText(InterviewResumeFile resumeFile) {
		try {
			String extractedText = documentTextExtractor.extractText(
					resumeFile.filename(),
					resumeFile.mimeType(),
					resumeFile.content());
			String normalizedText = normalizeOptional(extractedText);
			if (normalizedText == null) {
				throw new InterviewException(InterviewErrorCode.INPUT_INVALID);
			}
			validateCodePointLength(normalizedText, MAX_RESUME_TEXT_CODE_POINTS);
			return normalizedText;
		}
		catch (DocumentException exception) {
			throw InterviewInputExceptionMapper.fromDocument(exception);
		}
	}

	private static void validateCodePointLength(String value, int maximum) {
		if (value != null && value.codePointCount(0, value.length()) > maximum) {
			throw new InterviewException(InterviewErrorCode.INPUT_INVALID);
		}
	}

	private static String normalizeOptional(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.replace("\r\n", "\n").replace('\r', '\n').strip();
		return normalized.isBlank() ? null : normalized;
	}
}
