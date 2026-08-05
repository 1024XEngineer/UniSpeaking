package com.unispeaking.domain.po.scene;

import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.TargetRoleSummary;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Persisted Interview identity, role snapshot, and completed asset metadata.
 */
public record InterviewRecord(
		String id,
		UUID userId,
		String sessionId,
		String jobTitle,
		InterviewDifficulty difficulty,
		TargetRoleSummary roleSummary,
		String recordingObjectKey,
		Integer recordingDurationSeconds,
		OffsetDateTime completedAt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	public InterviewRecord {
		requireText(id, "id");
		Objects.requireNonNull(userId, "userId must not be null");
		requireText(sessionId, "sessionId");
		requireText(jobTitle, "jobTitle");
		Objects.requireNonNull(difficulty, "difficulty must not be null");
		Objects.requireNonNull(roleSummary, "roleSummary must not be null");
		if (recordingObjectKey != null && recordingObjectKey.isBlank()) {
			throw new IllegalArgumentException(
					"recordingObjectKey must not be blank");
		}
		if (recordingDurationSeconds != null
				&& recordingDurationSeconds < 0) {
			throw new IllegalArgumentException(
					"recordingDurationSeconds must not be negative");
		}
		Objects.requireNonNull(createdAt, "createdAt must not be null");
		Objects.requireNonNull(updatedAt, "updatedAt must not be null");
	}

	private static void requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
	}
}
