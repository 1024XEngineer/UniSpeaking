package com.unispeaking.domain.vo.achievement;

import java.math.BigDecimal;
import java.util.Objects;

public record AchievementDefinition(
		String achievementId,
		int level,
		String title,
		String description,
		BigDecimal threshold) {

	public AchievementDefinition {
		if (achievementId == null || achievementId.isBlank()) {
			throw new IllegalArgumentException("achievementId must not be blank");
		}
		if (level < 1) {
			throw new IllegalArgumentException("level must be positive");
		}
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("title must not be blank");
		}
		if (description == null || description.isBlank()) {
			throw new IllegalArgumentException("description must not be blank");
		}
		Objects.requireNonNull(threshold, "threshold must not be null");
		if (threshold.signum() <= 0) {
			throw new IllegalArgumentException("threshold must be positive");
		}
	}
}
