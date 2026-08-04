package com.unispeaking.domain.vo.achievement;

import java.math.BigDecimal;
import java.util.Objects;

public record AchievementMetricSnapshot(
		long completedConversationCount,
		int longestStreakDays,
		long distinctCompletedSceneCount,
		BigDecimal bestExpressionScore,
		long pronunciationAttemptCount,
		long historicalAssetCount,
		int bestMonthlyCheckinDays,
		long validPracticeSeconds,
		long activeDays,
		long qualitySessionCount) {

	public AchievementMetricSnapshot {
		Objects.requireNonNull(
				bestExpressionScore,
				"bestExpressionScore must not be null");
		if (completedConversationCount < 0
				|| longestStreakDays < 0
				|| distinctCompletedSceneCount < 0
				|| bestExpressionScore.signum() < 0
				|| pronunciationAttemptCount < 0
				|| historicalAssetCount < 0
				|| bestMonthlyCheckinDays < 0
				|| validPracticeSeconds < 0
				|| activeDays < 0
				|| qualitySessionCount < 0) {
			throw new IllegalArgumentException(
					"achievement metrics must not be negative");
		}
	}
}
