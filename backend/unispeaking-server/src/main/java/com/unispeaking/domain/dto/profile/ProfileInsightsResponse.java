package com.unispeaking.domain.dto.profile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ProfileInsightsResponse(
		WeeklyGoals weeklyGoals,
		List<TrainingTypeDistribution> trainingTypeDistribution,
		List<AbilityTrendPoint> abilityTrends) {

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

	public record AbilityTrendPoint(
			String sessionId,
			OffsetDateTime completedAt,
			String trainingType,
			AbilityScores scores) {
	}

	public record AbilityScores(
			BigDecimal accuracy,
			BigDecimal fluency,
			BigDecimal grammar,
			BigDecimal vocabulary,
			BigDecimal naturalness) {
	}
}
