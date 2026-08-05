package com.unispeaking.domain.dto.scene;

import java.time.Instant;

public record InterviewHeartbeatResponse(
		String interviewId,
		InterviewRuntimeStatus status,
		Instant lastSeen) {
}
