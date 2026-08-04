package com.unispeaking.common.exception.audio;

/**
 * 通用音频处理的稳定业务错误码。
 */
public enum AudioErrorCode {

	INPUT_REQUIRED(
			"AUDIO_INPUT_REQUIRED",
			"Audio input is required"),
	FORMAT_UNSUPPORTED(
			"AUDIO_FORMAT_UNSUPPORTED",
			"Only uncompressed 16-bit PCM WAV audio is supported"),
	CONTENT_INVALID(
			"AUDIO_CONTENT_INVALID",
			"Audio input is invalid"),
	ENCODING_FAILED(
			"AUDIO_ENCODING_FAILED",
			"Audio encoding failed");

	private final String code;
	private final String defaultMessage;

	AudioErrorCode(String code, String defaultMessage) {
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
