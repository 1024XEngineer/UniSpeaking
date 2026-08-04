package com.unispeaking.common.exception.audio;

import com.unispeaking.common.exception.BusinessException;
import java.util.Objects;

/**
 * 不暴露原始音频内容或底层编码器细节的通用音频业务异常。
 */
public final class AudioException extends BusinessException {

	private final AudioErrorCode errorCode;

	public AudioException(AudioErrorCode errorCode) {
		this(errorCode, null);
	}

	public AudioException(AudioErrorCode errorCode, Throwable cause) {
		super(required(errorCode).code(), errorCode.defaultMessage());
		this.errorCode = errorCode;
		if (cause != null) {
			initCause(cause);
		}
	}

	public AudioErrorCode errorCode() {
		return errorCode;
	}

	private static AudioErrorCode required(AudioErrorCode errorCode) {
		return Objects.requireNonNull(errorCode, "errorCode must not be null");
	}
}
