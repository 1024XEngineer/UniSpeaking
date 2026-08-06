package com.unispeaking.common.exception.interview;

import com.unispeaking.common.exception.BusinessException;
import java.util.Objects;

public final class InterviewException extends BusinessException {

	private final InterviewErrorCode errorCode;

	public InterviewException(InterviewErrorCode errorCode) {
		this(errorCode, null);
	}

	public InterviewException(InterviewErrorCode errorCode, Throwable cause) {
		super(required(errorCode).code(), errorCode.defaultMessage());
		this.errorCode = errorCode;
		if (cause != null) {
			initCause(cause);
		}
	}

	public InterviewErrorCode errorCode() {
		return errorCode;
	}

	private static InterviewErrorCode required(InterviewErrorCode errorCode) {
		return Objects.requireNonNull(errorCode, "errorCode must not be null");
	}
}
