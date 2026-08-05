package com.unispeaking.domain.dto.profile;

import java.time.OffsetDateTime;
import java.util.List;

public record ProfileInsightsResponse(
		WeeklyGoals weeklyGoals,
		List<TrainingTypeDistribution> trainingTypeDistribution) {

	public record WeeklyGoals(
			OffsetDateTime weekStartsAt,
			OffsetDateTime weekEndsAt,
			int durationTargetMinutes,
			long completedDurationSeconds,
			long remainingDurationSeconds,
			double durationProgress,
			boolean durationAchieved,
			int trainingCountTarget,
			long completedTrainingCount,
			long remainingTrainingCount,
			double countProgress,
			boolean countAchieved) {
	}

	public record TrainingTypeDistribution(
			String type,
			long durationSeconds,
			double percentage) {
	}
}
