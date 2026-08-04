package com.unispeaking.domain.vo.achievement;

import java.util.List;

public record AchievementSeriesDefinition(
		String seriesId,
		String category,
		String title,
		String unit,
		List<AchievementDefinition> milestones) {

	public AchievementSeriesDefinition {
		if (seriesId == null || seriesId.isBlank()) {
			throw new IllegalArgumentException("seriesId must not be blank");
		}
		if (category == null || category.isBlank()) {
			throw new IllegalArgumentException("category must not be blank");
		}
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("title must not be blank");
		}
		if (unit == null || unit.isBlank()) {
			throw new IllegalArgumentException("unit must not be blank");
		}
		milestones = milestones == null ? List.of() : List.copyOf(milestones);
		if (milestones.isEmpty()) {
			throw new IllegalArgumentException("milestones must not be empty");
		}
		for (int index = 0; index < milestones.size(); index++) {
			AchievementDefinition milestone = milestones.get(index);
			int expectedLevel = index + 1;
			if (milestone.level() != expectedLevel) {
				throw new IllegalArgumentException(
						"milestone levels must be continuous from one");
			}
			if (!milestone.achievementId().equals(seriesId + "-" + expectedLevel)) {
				throw new IllegalArgumentException(
						"achievementId must match seriesId and level");
			}
			if (index > 0 && milestone.threshold().compareTo(
					milestones.get(index - 1).threshold()) <= 0) {
				throw new IllegalArgumentException(
						"milestone thresholds must increase");
			}
		}
	}
}
