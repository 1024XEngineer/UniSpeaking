package com.unispeaking.domain.po.achievement;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserAchievementProgress(
		UUID userId,
		UUID achievementId,
		long progressValue,
		Instant unlockedAt,
		Instant createdAt,
		Instant updatedAt) {

	public UserAchievementProgress {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(achievementId, "achievementId");
		if (progressValue < 0) {
			throw new IllegalArgumentException("progressValue must not be negative");
		}
	}
}
