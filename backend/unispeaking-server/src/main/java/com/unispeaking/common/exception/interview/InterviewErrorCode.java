package com.unispeaking.common.exception.interview;

import org.springframework.http.HttpStatus;

public enum InterviewErrorCode {

	NOT_FOUND("INTERVIEW_NOT_FOUND", "Interview was not found", HttpStatus.NOT_FOUND),
	QUESTION_CONFLICT(
			"INTERVIEW_QUESTION_CONFLICT",
			"Answer does not match the current interview question",
			HttpStatus.CONFLICT),
	SUBMISSION_CONFLICT(
			"INTERVIEW_SUBMISSION_CONFLICT",
			"Submission identifier conflicts with an existing answer",
			HttpStatus.CONFLICT),
	SUBMISSION_RETRY_REQUIRED(
			"INTERVIEW_SUBMISSION_RETRY_REQUIRED",
			"A retryable answer submission must be retried before ending",
			HttpStatus.CONFLICT),
	DATA_CONFIRMATION_REQUIRED(
			"INTERVIEW_DATA_CONFIRMATION_REQUIRED",
			"Ending this interview requires insufficient-data confirmation",
			HttpStatus.CONFLICT),
	FINALIZATION_IN_PROGRESS(
			"INTERVIEW_FINALIZATION_IN_PROGRESS",
			"Interview finalization is already in progress",
			HttpStatus.CONFLICT),
	INPUT_INVALID(
			"INTERVIEW_INPUT_INVALID",
			"Interview input is invalid",
			HttpStatus.UNPROCESSABLE_ENTITY),
	STATE_INVALID(
			"INTERVIEW_STATE_INVALID",
			"Interview state does not allow this operation",
			HttpStatus.UNPROCESSABLE_ENTITY),
	AUDIO_INVALID(
			"INTERVIEW_AUDIO_INVALID",
			"Answer audio must be a supported PCM WAV file",
			HttpStatus.UNPROCESSABLE_ENTITY),
	PAYLOAD_TOO_LARGE(
			"INTERVIEW_PAYLOAD_TOO_LARGE",
			"Interview request exceeds the supported size limit",
			HttpStatus.PAYLOAD_TOO_LARGE),
	MEDIA_TYPE_UNSUPPORTED(
			"INTERVIEW_MEDIA_TYPE_UNSUPPORTED",
			"Interview request media type is not supported",
			HttpStatus.UNSUPPORTED_MEDIA_TYPE),
	DEPENDENCY_FAILED(
			"INTERVIEW_DEPENDENCY_FAILED",
			"An interview dependency failed",
			HttpStatus.BAD_GATEWAY),
	FINALIZATION_FAILED(
			"INTERVIEW_FINALIZATION_FAILED",
			"Interview assets could not be finalized",
			HttpStatus.BAD_GATEWAY),
	EXECUTOR_REJECTED(
			"INTERVIEW_EXECUTOR_REJECTED",
			"Interview processing capacity is temporarily unavailable",
			HttpStatus.SERVICE_UNAVAILABLE),
	SERVICE_UNAVAILABLE(
			"INTERVIEW_SERVICE_UNAVAILABLE",
			"Interview service is temporarily unavailable",
			HttpStatus.SERVICE_UNAVAILABLE);

	private final String code;
	private final String defaultMessage;
	private final HttpStatus status;

	InterviewErrorCode(String code, String defaultMessage, HttpStatus status) {
		this.code = code;
		this.defaultMessage = defaultMessage;
		this.status = status;
	}

	public String code() { return code; }
	public String defaultMessage() { return defaultMessage; }
	public HttpStatus status() { return status; }
}
