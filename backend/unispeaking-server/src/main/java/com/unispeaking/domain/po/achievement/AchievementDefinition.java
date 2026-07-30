package com.unispeaking.domain.po.achievement;

import com.unispeaking.domain.vo.achievement.AchievementMetricKey;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AchievementDefinition(
		UUID id,
		String code,
		String name,
		String description,
		String category,
		AchievementMetricKey metricKey,
		long targetValue,
		String iconKey,
		int sortOrder,
		Instant createdAt,
		Instant updatedAt) {

	public AchievementDefinition {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(description, "description");
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(metricKey, "metricKey");
		Objects.requireNonNull(iconKey, "iconKey");
		if (targetValue <= 0) {
			throw new IllegalArgumentException("targetValue must be positive");
		}
		if (sortOrder < 0) {
			throw new IllegalArgumentException("sortOrder must not be negative");
		}
	}
}
