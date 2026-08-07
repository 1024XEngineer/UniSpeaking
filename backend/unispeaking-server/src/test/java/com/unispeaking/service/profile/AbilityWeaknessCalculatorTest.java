package com.unispeaking.service.profile;

import com.unispeaking.component.profile.AbilityWeaknessCalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.dto.profile.ProfileInsightsResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbilityWeaknessCalculatorTest {

	private final AbilityWeaknessCalculator calculator =
			new AbilityWeaknessCalculator();

	@Test
	void doesNotIdentifyWeaknessesUntilMinimumSampleCount() {
		var result = calculator.calculate(List.of(
				point(1, 70, 70, 70, 70, 70),
				point(2, 72, 72, 72, 72, 72)));

		assertEquals(2, result.analysis().sampleCount());
		assertEquals(3, result.analysis().minimumSampleCount());
		assertFalse(result.analysis().reliable());
		assertEquals(List.of(), result.weaknesses());
		assertEquals(List.of(), result.recommendations());
	}

	@Test
	void returnsTwoLowestAveragesAndTheirRecentChanges() {
		var result = calculator.calculate(List.of(
				point(1, 80, 45, 60, 30, 75),
				point(2, 82, 50, 62, 35, 76),
				point(3, 84, 55, 64, 42, 77)));

		assertTrue(result.analysis().reliable());
		assertEquals(3, result.analysis().sampleCount());
		assertEquals(2, result.weaknesses().size());
		var primary = result.weaknesses().getFirst();
		assertEquals("vocabulary", primary.dimension());
		assertEquals(1, primary.rank());
		assertEquals(new BigDecimal("35.7"), primary.averageScore());
		assertEquals(new BigDecimal("12.0"), primary.recentChange());
		assertEquals("最近 3 次有效评分平均分最低", primary.basis());
		var secondary = result.weaknesses().getLast();
		assertEquals("fluency", secondary.dimension());
		assertEquals(new BigDecimal("50.0"), secondary.averageScore());
		assertEquals(new BigDecimal("10.0"), secondary.recentChange());
		assertEquals("FREE_CHAT", result.recommendations().getLast().trainingType());
		assertEquals("CUSTOM_SCENE",
				result.recommendations().getFirst().trainingType());
	}

	@Test
	void usesFixedDimensionOrderWhenAveragesAreEqual() {
		var result = calculator.calculate(List.of(
				point(1, 40, 40, 40, 40, 80),
				point(2, 40, 40, 40, 40, 80),
				point(3, 40, 40, 40, 40, 80)));

		assertEquals("accuracy", result.weaknesses().getFirst().dimension());
		assertEquals("fluency", result.weaknesses().getLast().dimension());
	}

	private ProfileInsightsResponse.AbilityTrendPoint point(
			int day,
			double accuracy,
			double fluency,
			double grammar,
			double vocabulary,
			double naturalness) {
		return new ProfileInsightsResponse.AbilityTrendPoint(
				"session_" + day,
				OffsetDateTime.parse("2026-08-0" + day + "T12:00:00+08:00"),
				"CUSTOM_SCENE",
				new ProfileInsightsResponse.AbilityScores(
						BigDecimal.valueOf(accuracy),
						BigDecimal.valueOf(fluency),
						BigDecimal.valueOf(grammar),
						BigDecimal.valueOf(vocabulary),
						BigDecimal.valueOf(naturalness)));
	}
}
