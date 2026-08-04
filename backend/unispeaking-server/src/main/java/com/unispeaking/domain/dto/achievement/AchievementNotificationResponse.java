package com.unispeaking.domain.dto.achievement;

import java.time.Instant;

public record AchievementNotificationResponse(
		String achievementId,
		String seriesId,
		String category,
		String seriesTitle,
		int level,
		String title,
		String description,
		Instant unlockedAt,
		Instant acknowledgedAt) {
}
