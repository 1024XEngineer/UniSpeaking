package com.unispeaking.domain.vo.scene;

import com.unispeaking.common.exception.interview.InterviewErrorCode;
import com.unispeaking.common.exception.interview.InterviewException;
import com.unispeaking.common.exception.interview.InterviewInputExceptionMapper;
import com.unispeaking.common.exception.ocr.OcrException;
import com.unispeaking.domain.dto.ocr.OcrImage;
import com.unispeaking.provider.OcrProvider;
import java.util.List;
import java.util.Objects;

/**
 * Recognizes temporary JD text without persistence or logging side effects.
 */
public final class InterviewJobDescriptionOcrRecognizer {

	static final int MAX_TEXT_CODE_POINTS = 5_000;

	private final OcrProvider ocrProvider;

	public InterviewJobDescriptionOcrRecognizer(OcrProvider ocrProvider) {
		this.ocrProvider = Objects.requireNonNull(ocrProvider, "ocrProvider must not be null");
	}

	public InterviewJobDescriptionOcrText recognize(List<OcrImage> images) {
		InterviewJobDescriptionOcrBatch batch = new InterviewJobDescriptionOcrBatch(images);
		try {
			if (!ocrProvider.available()) {
				throw new InterviewException(InterviewErrorCode.SERVICE_UNAVAILABLE);
			}
			String recognizedText = ocrProvider.recognizeText(batch.images());
			String normalizedText = normalizeRecognizedText(recognizedText);
			return new InterviewJobDescriptionOcrText(normalizedText);
		}
		catch (InterviewException exception) {
			throw exception;
		}
		catch (OcrException exception) {
			throw InterviewInputExceptionMapper.fromOcr(exception);
		}
		catch (RuntimeException exception) {
			throw new InterviewException(InterviewErrorCode.DEPENDENCY_FAILED, exception);
		}
	}

	private static String normalizeRecognizedText(String text) {
		if (text == null || text.isBlank()) {
			throw new InterviewException(InterviewErrorCode.INPUT_INVALID);
		}
		String normalized = text.replace("\r\n", "\n").replace('\r', '\n').strip();
		if (normalized.isBlank()
				|| normalized.codePointCount(0, normalized.length()) > MAX_TEXT_CODE_POINTS) {
			throw new InterviewException(InterviewErrorCode.INPUT_INVALID);
		}
		return normalized;
	}
}
