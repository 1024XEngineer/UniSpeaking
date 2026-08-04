package com.unispeaking.domain.dto.achievement;

import java.math.BigDecimal;
import java.util.List;

public record AchievementSeriesResponse(
		String seriesId,
		String category,
		String title,
		String unit,
		BigDecimal currentValue,
		int currentLevel,
		String currentTitle,
		Integer nextLevel,
		String nextTitle,
		BigDecimal nextThreshold,
		boolean completed,
		List<AchievementMilestoneResponse> milestones) {

	public AchievementSeriesResponse {
		milestones = milestones == null ? List.of() : List.copyOf(milestones);
	}
}
