package com.unispeaking.service.achievement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.vo.achievement.AchievementMetricSnapshot;
import com.unispeaking.domain.vo.achievement.AchievementSeriesProgress;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AchievementProgressCalculatorTest {

	private final AchievementProgressCalculator calculator =
			new AchievementProgressCalculator(new AchievementCatalog());

	@Test
	void mapsMetricsToCurrentAndNextMilestones() {
		AchievementMetricSnapshot metrics = new AchievementMetricSnapshot(
				20,
				7,
				10,
				new BigDecimal("95"),
				30,
				20,
				25,
				3599,
				365,
				5);

		List<AchievementSeriesProgress> progress = calculator.calculate(metrics);

		assertEquals(10, progress.size());
		AchievementSeriesProgress conversation = find(progress, "conversation");
		assertEquals(3, conversation.currentLevel());
		assertEquals("对话常客", conversation.currentTitle());
		assertEquals(4, conversation.nextMilestone().level());
		assertFalse(conversation.completed());
		AchievementSeriesProgress monthly = find(progress, "monthly-checkin");
		assertEquals(4, monthly.currentLevel());
		assertNull(monthly.nextMilestone());
		assertTrue(monthly.completed());
		AchievementSeriesProgress duration = find(progress, "practice-duration");
		assertEquals(BigDecimal.ZERO, duration.currentValue());
		assertEquals(0, duration.currentLevel());
		assertEquals(1, duration.nextMilestone().level());
		assertThrows(
				UnsupportedOperationException.class,
				() -> duration.reachedMilestones().clear());
	}

	@Test
	void unlocksFirstDurationLevelAtExactlyOneHour() {
		AchievementMetricSnapshot metrics = new AchievementMetricSnapshot(
				0,
				0,
				0,
				BigDecimal.ZERO,
				0,
				0,
				0,
				3600,
				0,
				0);

		AchievementSeriesProgress duration = find(
				calculator.calculate(metrics),
				"practice-duration");

		assertEquals(BigDecimal.ONE, duration.currentValue());
		assertEquals(1, duration.currentLevel());
		assertEquals(2, duration.nextMilestone().level());
	}

	@Test
	void rejectsNegativeMetricSnapshots() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new AchievementMetricSnapshot(
						-1,
						0,
						0,
						BigDecimal.ZERO,
						0,
						0,
						0,
						0,
						0,
						0));
	}

	private AchievementSeriesProgress find(
			List<AchievementSeriesProgress> progress,
			String seriesId) {
		return progress.stream()
				.filter(item -> item.series().seriesId().equals(seriesId))
				.findFirst()
				.orElseThrow();
	}
}
