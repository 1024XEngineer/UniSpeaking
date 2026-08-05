package com.unispeaking.service.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.po.profile.WeeklyLearningGoals;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WeeklyGoalProgressCalculatorTest {

	private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
	private static final Instant NOW = Instant.parse("2026-08-05T04:00:00Z");
	private final WeeklyGoalProgressCalculator calculator =
			new WeeklyGoalProgressCalculator();

	@Test
	void calculatesOverlappingDurationAndCompletionWeekCount() {
		var progress = calculator.calculate(
				List.of(
						record(
								SceneType.FREE_CHAT,
								SessionStatus.COMPLETED,
								"2026-08-02T15:59:30Z",
								"2026-08-02T16:00:30Z"),
						record(
								SceneType.CUSTOM_SCENE,
								SessionStatus.COMPLETED,
								"2026-08-04T01:00:00Z",
								"2026-08-04T01:05:00Z"),
						record(
								SceneType.FREE_CHAT,
								SessionStatus.COMPLETED,
								"2026-08-04T02:00:00Z",
								"2026-08-04T02:00:29Z"),
						record(
								SceneType.IELTS_SCENE,
								SessionStatus.COMPLETED,
								"2026-08-04T03:00:00Z",
								"2026-08-04T03:10:00Z"),
						record(
								SceneType.CUSTOM_SCENE,
								SessionStatus.FAILED,
								"2026-08-04T04:00:00Z",
								"2026-08-04T04:10:00Z")),
				new WeeklyLearningGoals(5, 4),
				NOW,
				ZONE_ID);

		assertEquals(330, progress.completedDurationSeconds());
		assertEquals(0, progress.remainingDurationSeconds());
		assertEquals(100.0, progress.durationProgress());
		assertTrue(progress.durationAchieved());
		assertEquals(2, progress.completedTrainingCount());
		assertEquals(2, progress.remainingTrainingCount());
		assertEquals(50.0, progress.countProgress());
		assertFalse(progress.countAchieved());
		assertEquals(Instant.parse("2026-08-02T16:00:00Z"),
				progress.weekStartsAt());
		assertEquals(Instant.parse("2026-08-09T16:00:00Z"),
				progress.weekEndsAt());
	}

	@Test
	void roundsProgressToOneDecimalAndReturnsRemainingValues() {
		var progress = calculator.calculate(
				List.of(record(
						SceneType.FREE_CHAT,
						SessionStatus.COMPLETED,
						"2026-08-04T01:00:00Z",
						"2026-08-04T02:16:00Z")),
				new WeeklyLearningGoals(120, 5),
				NOW,
				ZONE_ID);

		assertEquals(4560, progress.completedDurationSeconds());
		assertEquals(2640, progress.remainingDurationSeconds());
		assertEquals(63.3, progress.durationProgress());
		assertEquals(20.0, progress.countProgress());
	}

	private PracticeSessionRecord record(
			SceneType type,
			SessionStatus status,
			String startedAt,
			String endedAt) {
		return new PracticeSessionRecord(
				type.name() + startedAt,
				UUID.randomUUID(),
				type.sceneIdPrefix() + "_scene",
				type,
				status,
				Instant.parse(startedAt),
				Instant.parse(endedAt));
	}
}
