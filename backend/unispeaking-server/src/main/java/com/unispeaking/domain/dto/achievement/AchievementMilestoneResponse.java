package com.unispeaking.domain.dto.achievement;

import java.math.BigDecimal;
import java.time.Instant;

public record AchievementMilestoneResponse(
		String achievementId,
		int level,
		String title,
		String description,
		BigDecimal threshold,
		boolean unlocked,
		Instant unlockedAt) {
}
