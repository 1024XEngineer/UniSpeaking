package com.unispeaking.service.achievement;

import com.unispeaking.domain.vo.achievement.AchievementDefinition;
import com.unispeaking.domain.vo.achievement.AchievementMetricSnapshot;
import com.unispeaking.domain.vo.achievement.AchievementSeriesDefinition;
import com.unispeaking.domain.vo.achievement.AchievementSeriesProgress;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AchievementProgressCalculator {

	private static final long SECONDS_PER_HOUR = 3600;

	private final AchievementCatalog catalog;

	public AchievementProgressCalculator(AchievementCatalog catalog) {
		this.catalog = catalog;
	}

	public List<AchievementSeriesProgress> calculate(
			AchievementMetricSnapshot metrics) {
		return catalog.series().stream()
				.map(series -> calculateSeries(series, currentValue(series, metrics)))
				.toList();
	}

	private AchievementSeriesProgress calculateSeries(
			AchievementSeriesDefinition series,
			BigDecimal currentValue) {
		List<AchievementDefinition> reached = series.milestones().stream()
				.filter(milestone -> currentValue.compareTo(
						milestone.threshold()) >= 0)
				.toList();
		AchievementDefinition next = series.milestones().stream()
				.filter(milestone -> currentValue.compareTo(
						milestone.threshold()) < 0)
				.findFirst()
				.orElse(null);
		return new AchievementSeriesProgress(series, currentValue, reached, next);
	}

	private BigDecimal currentValue(
			AchievementSeriesDefinition series,
			AchievementMetricSnapshot metrics) {
		return switch (series.seriesId()) {
			case "conversation" -> value(metrics.completedConversationCount());
			case "streak" -> value(metrics.longestStreakDays());
			case "scene-exploration" -> value(
					metrics.distinctCompletedSceneCount());
			case "expression-score" -> metrics.bestExpressionScore();
			case "pronunciation-attempt" -> value(
					metrics.pronunciationAttemptCount());
			case "asset-collection" -> value(metrics.historicalAssetCount());
			case "monthly-checkin" -> value(metrics.bestMonthlyCheckinDays());
			case "practice-duration" -> value(
					metrics.validPracticeSeconds() / SECONDS_PER_HOUR);
			case "active-days" -> value(metrics.activeDays());
			case "quality-sessions" -> value(metrics.qualitySessionCount());
			default -> throw new IllegalStateException(
					"Unsupported achievement series: " + series.seriesId());
		};
	}

	private BigDecimal value(long value) {
		return BigDecimal.valueOf(value);
	}
}
