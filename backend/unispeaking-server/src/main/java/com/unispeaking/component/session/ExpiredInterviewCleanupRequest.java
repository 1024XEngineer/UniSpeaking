package com.unispeaking.component.session;

import java.time.Instant;
import java.util.Objects;

public record ExpiredInterviewCleanupRequest(
		String sessionId,
		String interviewId,
		String userId,
		Instant lastSeen,
		Instant expiredAt) {

	public ExpiredInterviewCleanupRequest {
		sessionId = requireText(sessionId, "sessionId");
		interviewId = requireText(interviewId, "interviewId");
		userId = requireText(userId, "userId");
		lastSeen = Objects.requireNonNull(lastSeen, "lastSeen");
		expiredAt = Objects.requireNonNull(expiredAt, "expiredAt");
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value.trim();
	}
}
