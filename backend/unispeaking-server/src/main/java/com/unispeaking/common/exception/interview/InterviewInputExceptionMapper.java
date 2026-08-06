package com.unispeaking.common.exception.interview;

import com.unispeaking.common.exception.document.DocumentErrorCode;
import com.unispeaking.common.exception.document.DocumentException;
import com.unispeaking.common.exception.ocr.OcrErrorCode;
import com.unispeaking.common.exception.ocr.OcrException;
import java.util.Objects;

/**
 * Maps reusable document and OCR failures to the stable Interview API boundary.
 */
public final class InterviewInputExceptionMapper {

	private InterviewInputExceptionMapper() {
	}

	public static InterviewException fromDocument(DocumentException exception) {
		Objects.requireNonNull(exception, "exception must not be null");
		DocumentErrorCode errorCode = exception.errorCode();
		InterviewErrorCode mapped = switch (errorCode) {
			case INPUT_REQUIRED, CONTENT_INVALID, PDF_PAGE_LIMIT_EXCEEDED,
					TEXT_EMPTY, TEXT_TOO_LARGE -> InterviewErrorCode.INPUT_INVALID;
			case TOO_LARGE -> InterviewErrorCode.PAYLOAD_TOO_LARGE;
			case FORMAT_UNSUPPORTED -> InterviewErrorCode.MEDIA_TYPE_UNSUPPORTED;
		};
		return new InterviewException(mapped, exception);
	}

	public static InterviewException fromOcr(OcrException exception) {
		Objects.requireNonNull(exception, "exception must not be null");
		OcrErrorCode errorCode = exception.errorCode();
		InterviewErrorCode mapped = switch (errorCode) {
			case INPUT_REQUIRED, TOO_MANY_IMAGES, CONTENT_INVALID, PIXEL_LIMIT_EXCEEDED ->
					InterviewErrorCode.INPUT_INVALID;
			case TOTAL_SIZE_EXCEEDED -> InterviewErrorCode.PAYLOAD_TOO_LARGE;
			case FORMAT_UNSUPPORTED -> InterviewErrorCode.MEDIA_TYPE_UNSUPPORTED;
			case UNAVAILABLE -> InterviewErrorCode.SERVICE_UNAVAILABLE;
			case TIMEOUT, PROCESS_FAILED, RESPONSE_INVALID -> InterviewErrorCode.DEPENDENCY_FAILED;
		};
		return new InterviewException(mapped, exception);
	}
}
