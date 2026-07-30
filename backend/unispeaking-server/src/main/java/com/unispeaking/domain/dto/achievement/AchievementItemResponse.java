package com.unispeaking.domain.dto.achievement;

import com.unispeaking.domain.po.achievement.AchievementDefinition;
import com.unispeaking.domain.po.achievement.UserAchievementProgress;
import java.time.Instant;

public record AchievementItemResponse(
		String code,
		String name,
		String description,
		String category,
		String iconKey,
		long progressValue,
		long targetValue,
		boolean unlocked,
		Instant unlockedAt) {

	public static AchievementItemResponse from(
			AchievementDefinition definition,
			UserAchievementProgress progress) {
		return new AchievementItemResponse(
				definition.code(),
				definition.name(),
				definition.description(),
				definition.category(),
				definition.iconKey(),
				progress.progressValue(),
				definition.targetValue(),
				progress.unlockedAt() != null,
				progress.unlockedAt());
	}
}
