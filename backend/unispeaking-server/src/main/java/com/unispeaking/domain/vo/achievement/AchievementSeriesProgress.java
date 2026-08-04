package com.unispeaking.domain.vo.achievement;

import java.math.BigDecimal;
import java.util.List;

public record AchievementSeriesProgress(
		AchievementSeriesDefinition series,
		BigDecimal currentValue,
		List<AchievementDefinition> reachedMilestones,
		AchievementDefinition nextMilestone) {

	public AchievementSeriesProgress {
		if (series == null) {
			throw new IllegalArgumentException("series must not be null");
		}
		if (currentValue == null || currentValue.signum() < 0) {
			throw new IllegalArgumentException("currentValue must not be negative");
		}
		reachedMilestones = reachedMilestones == null
				? List.of()
				: List.copyOf(reachedMilestones);
	}

	public int currentLevel() {
		return reachedMilestones.isEmpty()
				? 0
				: reachedMilestones.getLast().level();
	}

	public String currentTitle() {
		return reachedMilestones.isEmpty()
				? null
				: reachedMilestones.getLast().title();
	}

	public boolean completed() {
		return nextMilestone == null;
	}
}
