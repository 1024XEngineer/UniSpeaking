package com.unispeaking.common.exception.ocr;

/**
 * 通用图片 OCR 的稳定业务错误码。
 */
public enum OcrErrorCode {

	INPUT_REQUIRED(
			"OCR_INPUT_REQUIRED",
			"OCR image input is required"),
	TOO_MANY_IMAGES(
			"OCR_TOO_MANY_IMAGES",
			"OCR accepts at most 5 images per request"),
	TOTAL_SIZE_EXCEEDED(
			"OCR_TOTAL_SIZE_EXCEEDED",
			"OCR images exceed the supported total size limit"),
	FORMAT_UNSUPPORTED(
			"OCR_FORMAT_UNSUPPORTED",
			"Only PNG and JPEG images are supported"),
	CONTENT_INVALID(
			"OCR_CONTENT_INVALID",
			"OCR image content is invalid"),
	PIXEL_LIMIT_EXCEEDED(
			"OCR_PIXEL_LIMIT_EXCEEDED",
			"OCR image exceeds the supported pixel limit"),
	UNAVAILABLE(
			"OCR_UNAVAILABLE",
			"OCR provider is not available"),
	TIMEOUT(
			"OCR_TIMEOUT",
			"OCR processing timed out"),
	PROCESS_FAILED(
			"OCR_PROCESS_FAILED",
			"OCR process failed"),
	RESPONSE_INVALID(
			"OCR_RESPONSE_INVALID",
			"OCR response is invalid");

	private final String code;
	private final String defaultMessage;

	OcrErrorCode(String code, String defaultMessage) {
		this.code = code;
		this.defaultMessage = defaultMessage;
	}

	public String code() {
		return code;
	}

	public String defaultMessage() {
		return defaultMessage;
	}
}
