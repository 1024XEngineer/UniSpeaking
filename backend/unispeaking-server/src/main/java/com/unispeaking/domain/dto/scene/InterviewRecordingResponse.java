package com.unispeaking.domain.dto.scene;

import java.time.Instant;

public record InterviewRecordingResponse(
		String url,
		Instant expiresAt) {
}
