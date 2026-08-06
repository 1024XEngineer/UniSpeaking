package com.unispeaking.domain.dto.scene;

public record InterviewRecordingMetadataResponse(
		int durationSeconds) {

	public InterviewRecordingMetadataResponse {
		if (durationSeconds < 0) {
			throw new IllegalArgumentException("durationSeconds must not be negative");
		}
	}
}
