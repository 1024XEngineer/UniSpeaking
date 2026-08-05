package com.unispeaking.common.exception.ocr;

import com.unispeaking.common.exception.BusinessException;
import java.util.Objects;

/**
 * 不暴露图片内容、识别正文或底层进程输出的通用 OCR 业务异常。
 */
public final class OcrException extends BusinessException {

	private final OcrErrorCode errorCode;

	public OcrException(OcrErrorCode errorCode) {
		this(errorCode, null);
	}

	public OcrException(OcrErrorCode errorCode, Throwable cause) {
		super(required(errorCode).code(), errorCode.defaultMessage());
		this.errorCode = errorCode;
		if (cause != null) {
			initCause(cause);
		}
	}

	public OcrErrorCode errorCode() {
		return errorCode;
	}

	private static OcrErrorCode required(OcrErrorCode errorCode) {
		return Objects.requireNonNull(errorCode, "errorCode must not be null");
	}
}
