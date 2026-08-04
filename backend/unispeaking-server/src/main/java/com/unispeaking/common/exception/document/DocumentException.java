package com.unispeaking.common.exception.document;

import com.unispeaking.common.exception.BusinessException;
import java.util.Objects;

/**
 * 不暴露原始文档内容或底层解析细节的通用文档业务异常。
 */
public final class DocumentException extends BusinessException {

	private final DocumentErrorCode errorCode;

	public DocumentException(DocumentErrorCode errorCode) {
		this(errorCode, null);
	}

	public DocumentException(DocumentErrorCode errorCode, Throwable cause) {
		super(required(errorCode).code(), errorCode.defaultMessage());
		this.errorCode = errorCode;
		if (cause != null) {
			initCause(cause);
		}
	}

	public DocumentErrorCode errorCode() {
		return errorCode;
	}

	private static DocumentErrorCode required(DocumentErrorCode errorCode) {
		return Objects.requireNonNull(errorCode, "errorCode must not be null");
	}
}
