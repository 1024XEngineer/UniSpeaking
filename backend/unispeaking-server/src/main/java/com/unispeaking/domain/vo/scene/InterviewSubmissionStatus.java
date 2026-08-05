package com.unispeaking.domain.vo.scene;

public enum InterviewSubmissionStatus {

	ACCEPTED,
	PROCESSING,
	COMPLETED,
	FAILED_RETRYABLE,
	FAILED_TERMINAL;

	public boolean isInFlight() {
		return this == ACCEPTED || this == PROCESSING;
	}

	public boolean isTerminal() {
		return this == COMPLETED
				|| this == FAILED_RETRYABLE
				|| this == FAILED_TERMINAL;
	}
}
