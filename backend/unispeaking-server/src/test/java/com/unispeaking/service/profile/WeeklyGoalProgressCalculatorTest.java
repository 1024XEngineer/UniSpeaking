package com.unispeaking.service.profile;

import com.unispeaking.component.profile.WeeklyGoalProgressCalculator;

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

		assertEquals(930, progress.completedDurationSeconds());
		assertEquals(0, progress.remainingDurationSeconds());
		assertEquals(100.0, progress.durationProgress());
		assertTrue(progress.durationAchieved());
		assertEquals(3, progress.completedTrainingCount());
		assertEquals(1, progress.remainingTrainingCount());
		assertEquals(75.0, progress.countProgress());
		assertFalse(progress.countAchieved());
		assertEquals(3, progress.trainingTypeDurations().size());
		assertEquals(SceneType.FREE_CHAT,
				progress.trainingTypeDurations().get(0).type());
		assertEquals(30,
				progress.trainingTypeDurations().get(0).durationSeconds());
		assertEquals(3.2,
				progress.trainingTypeDurations().get(0).percentage());
		assertEquals(SceneType.CUSTOM_SCENE,
				progress.trainingTypeDurations().get(1).type());
		assertEquals(300,
				progress.trainingTypeDurations().get(1).durationSeconds());
		assertEquals(32.3,
				progress.trainingTypeDurations().get(1).percentage());
		assertEquals(SceneType.IELTS_SCENE,
				progress.trainingTypeDurations().get(2).type());
		assertEquals(600,
				progress.trainingTypeDurations().get(2).durationSeconds());
		assertEquals(64.5,
				progress.trainingTypeDurations().get(2).percentage());
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
		assertEquals(1, progress.trainingTypeDurations().size());
		assertEquals(4560,
				progress.trainingTypeDurations().getFirst().durationSeconds());
		assertEquals(100.0,
				progress.trainingTypeDurations().getFirst().percentage());
	}

	@Test
	void includesIeltsSessionsInLearningDuration() {
		var progress = calculator.calculate(
				List.of(record(
						SceneType.IELTS_SCENE,
						SessionStatus.COMPLETED,
						"2026-08-04T01:00:00Z",
						"2026-08-04T01:05:00Z")),
				WeeklyLearningGoals.defaults(),
				NOW,
				ZONE_ID);

		assertEquals(300, progress.completedDurationSeconds());
		assertEquals(1, progress.completedTrainingCount());
		assertEquals(SceneType.IELTS_SCENE,
				progress.trainingTypeDurations().getFirst().type());
	}

	@Test
	void handlesNullAndEmptyRecordsWithZeroTargets() {
		var nullProgress = calculator.calculate(
				null, new WeeklyLearningGoals(0, 0), NOW, ZONE_ID);
		var emptyProgress = calculator.calculate(
				List.of(), new WeeklyLearningGoals(0, 0), NOW, ZONE_ID);

		assertEquals(0, nullProgress.completedDurationSeconds());
		assertEquals(0.0, nullProgress.durationProgress());
		assertEquals(0.0, nullProgress.countProgress());
		assertTrue(nullProgress.durationAchieved());
		assertTrue(nullProgress.countAchieved());
		assertEquals(List.of(), emptyProgress.trainingTypeDurations());
	}

	@Test
	void filtersEveryIneligibleSessionShape() {
		List<PracticeSessionRecord> records = List.of(
				raw(SceneType.FREE_CHAT, SessionStatus.FAILED,
						Instant.parse("2026-08-04T01:00:00Z"), Instant.parse("2026-08-04T01:10:00Z")),
				raw(SceneType.INTERVIEW_SCENE, SessionStatus.COMPLETED,
						Instant.parse("2026-08-04T01:00:00Z"), Instant.parse("2026-08-04T01:10:00Z")),
				raw(null, SessionStatus.COMPLETED,
						Instant.parse("2026-08-04T01:00:00Z"), Instant.parse("2026-08-04T01:10:00Z")),
				raw(SceneType.FREE_CHAT, SessionStatus.COMPLETED,
						null, Instant.parse("2026-08-04T01:10:00Z")),
				raw(SceneType.FREE_CHAT, SessionStatus.COMPLETED,
						Instant.parse("2026-08-04T01:00:00Z"), null),
				raw(SceneType.FREE_CHAT, SessionStatus.COMPLETED,
						Instant.parse("2026-08-04T01:10:00Z"), Instant.parse("2026-08-04T01:00:00Z")),
				raw(SceneType.FREE_CHAT, SessionStatus.COMPLETED,
						Instant.parse("2026-08-04T01:00:00Z"), Instant.parse("2026-08-04T01:00:29Z")));

		var progress = calculator.calculate(records, WeeklyLearningGoals.defaults(), NOW, ZONE_ID);

		assertEquals(0, progress.completedDurationSeconds());
		assertEquals(0, progress.completedTrainingCount());
	}

	@Test
	void excludesSessionsOutsideWeekAndClipsFutureDurationAtNow() {
		var progress = calculator.calculate(
				List.of(
						record(SceneType.FREE_CHAT, SessionStatus.COMPLETED,
								"2026-08-02T14:00:00Z", "2026-08-02T15:00:00Z"),
						record(SceneType.FREE_CHAT, SessionStatus.COMPLETED,
								"2026-08-05T03:59:00Z", "2026-08-05T04:01:00Z"),
						record(SceneType.IELTS_SCENE, SessionStatus.COMPLETED,
								"2026-08-05T03:58:30Z", "2026-08-05T03:59:00Z"),
						record(SceneType.CUSTOM_SCENE, SessionStatus.COMPLETED,
								"2026-08-09T16:00:00Z", "2026-08-09T16:01:00Z")),
				new WeeklyLearningGoals(1, 1), NOW, ZONE_ID);

		assertEquals(90, progress.completedDurationSeconds());
		assertEquals(1, progress.completedTrainingCount());
		assertEquals(100.0, progress.durationProgress());
		assertEquals(100.0, progress.countProgress());
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

	private PracticeSessionRecord raw(
			SceneType type,
			SessionStatus status,
			Instant startedAt,
			Instant endedAt) {
		return new PracticeSessionRecord(
				UUID.randomUUID().toString(),
				UUID.randomUUID(),
				"scene",
				type,
				status,
				startedAt,
				endedAt);
	}
}
