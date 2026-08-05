package com.unispeaking.component.session;

import java.time.Instant;
import java.util.Objects;

public record ExpiredInterviewSubmissionCleanupRequest(
		String sessionId,
		String interviewId,
		String submissionId,
		Instant acceptedAt,
		Instant deadline) {

	public ExpiredInterviewSubmissionCleanupRequest {
		sessionId = requireText(sessionId, "sessionId");
		interviewId = requireText(interviewId, "interviewId");
		submissionId = requireText(submissionId, "submissionId");
		acceptedAt = Objects.requireNonNull(acceptedAt, "acceptedAt");
		deadline = Objects.requireNonNull(deadline, "deadline");
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value.trim();
	}
}
