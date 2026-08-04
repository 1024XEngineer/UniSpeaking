package com.unispeaking.domain.po.achievement;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserAchievementUnlock(
		UUID userId,
		String achievementId,
		Instant unlockedAt,
		Instant acknowledgedAt) {

	public UserAchievementUnlock {
		Objects.requireNonNull(userId, "userId must not be null");
		Objects.requireNonNull(achievementId, "achievementId must not be null");
		Objects.requireNonNull(unlockedAt, "unlockedAt must not be null");
		achievementId = achievementId.trim();
		if (achievementId.isEmpty()) {
			throw new IllegalArgumentException("achievementId must not be blank");
		}
		if (acknowledgedAt != null && acknowledgedAt.isBefore(unlockedAt)) {
			throw new IllegalArgumentException(
					"acknowledgedAt must not be before unlockedAt");
		}
	}

	public boolean pendingNotification() {
		return acknowledgedAt == null;
	}
}
