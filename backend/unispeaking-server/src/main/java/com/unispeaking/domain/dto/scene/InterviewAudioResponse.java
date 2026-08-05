package com.unispeaking.domain.dto.scene;

public record InterviewAudioResponse(
		String mimeType,
		String base64) {

	public InterviewAudioResponse {
		requireText(mimeType, "mimeType");
		requireText(base64, "base64");
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
	}
}
