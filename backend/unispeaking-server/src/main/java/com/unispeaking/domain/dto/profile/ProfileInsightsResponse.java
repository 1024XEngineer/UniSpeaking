package com.unispeaking.domain.dto.profile;

import java.time.OffsetDateTime;

public record ProfileInsightsResponse(WeeklyGoals weeklyGoals) {

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
}
