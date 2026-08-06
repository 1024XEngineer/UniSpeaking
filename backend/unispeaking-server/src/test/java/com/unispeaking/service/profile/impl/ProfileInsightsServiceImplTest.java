package com.unispeaking.service.profile.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.profile.UpdateWeeklyLearningGoalsRequest;
import com.unispeaking.domain.po.evaluation.SessionScoreSnapshot;
import com.unispeaking.domain.po.profile.WeeklyLearningGoals;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.user.WeeklyLearningGoalRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ProfileInsightsServiceImplTest {

	private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
	private static final Instant NOW = Instant.parse("2026-08-05T04:00:00Z");

	@Test
	void returnsDefaultTargetsAndRealWeeklyProgress() {
		UUID userId = UUID.randomUUID();
		WeeklyLearningGoalRepository goals =
				mock(WeeklyLearningGoalRepository.class);
		PracticeSessionRepository sessions =
				mock(PracticeSessionRepository.class);
		SessionEvaluationRepository evaluations =
				mock(SessionEvaluationRepository.class);
		when(goals.findByUserId(userId)).thenReturn(Optional.empty());
		when(sessions.findCompletedOverlapping(any(), any(), any()))
				.thenReturn(List.of(record(userId, 300)));
		when(sessions.findCompletedByUserId(userId)).thenReturn(List.of());
		ProfileInsightsServiceImpl service = service(goals, sessions, evaluations);

		var insights = service.getInsights(userId.toString());
		var response = insights.weeklyGoals();

		assertEquals(120, response.durationTargetMinutes());
		assertEquals(300, response.completedDurationSeconds());
		assertEquals(5, response.trainingCountTarget());
		assertEquals(1, response.completedTrainingCount());
		assertEquals("2026-08-03T00:00+08:00", response.weekStartsAt().toString());
		var distribution = insights.trainingTypeDistribution();
		assertEquals(1, distribution.size());
		assertEquals("FREE_CHAT", distribution.getFirst().type());
		assertEquals(300, distribution.getFirst().durationSeconds());
		assertEquals(100.0, distribution.getFirst().percentage());
		assertEquals(0, insights.weaknessAnalysis().sampleCount());
		assertFalse(insights.weaknessAnalysis().reliable());
		assertEquals(List.of(), insights.weaknesses());
		assertEquals(List.of(), insights.recommendations());
	}

	@Test
	void savesTargetsAndReturnsUpdatedProgress() {
		UUID userId = UUID.randomUUID();
		WeeklyLearningGoalRepository goals =
				mock(WeeklyLearningGoalRepository.class);
		PracticeSessionRepository sessions =
				mock(PracticeSessionRepository.class);
		SessionEvaluationRepository evaluations =
				mock(SessionEvaluationRepository.class);
		WeeklyLearningGoals updated = new WeeklyLearningGoals(180, 6);
		when(goals.findByUserId(userId)).thenReturn(Optional.of(updated));
		when(sessions.findCompletedOverlapping(any(), any(), any()))
				.thenReturn(List.of());
		when(sessions.findCompletedByUserId(userId)).thenReturn(List.of());
		ProfileInsightsServiceImpl service = service(goals, sessions, evaluations);

		var response = service.updateGoals(
				userId.toString(),
				new UpdateWeeklyLearningGoalsRequest(180, 6));

		verify(goals).save(userId, updated);
		assertEquals(180,
				response.weeklyGoals().durationTargetMinutes());
		assertEquals(6,
				response.weeklyGoals().trainingCountTarget());
		assertEquals(List.of(), response.trainingTypeDistribution());
		assertEquals(List.of(), response.abilityTrends());
		assertFalse(response.weaknessAnalysis().reliable());
	}

	@Test
	void returnsLatestTenOwnedReportsInCompletionOrder() {
		UUID userId = UUID.randomUUID();
		WeeklyLearningGoalRepository goals =
				mock(WeeklyLearningGoalRepository.class);
		PracticeSessionRepository sessions =
				mock(PracticeSessionRepository.class);
		SessionEvaluationRepository evaluations =
				mock(SessionEvaluationRepository.class);
		when(goals.findByUserId(userId))
				.thenReturn(Optional.of(WeeklyLearningGoals.defaults()));
		when(sessions.findCompletedOverlapping(any(), any(), any()))
				.thenReturn(List.of());
		Instant firstEnd = Instant.parse("2026-08-03T01:00:00Z");
		List<PracticeSessionRecord> completed = new ArrayList<>();
		IntStream.rangeClosed(1, 12).forEach(index -> completed.add(record(
				userId,
				"session_" + index,
				firstEnd.plusSeconds(index * 60L),
				index == 11 ? SceneType.CUSTOM_SCENE : SceneType.FREE_CHAT)));
		when(sessions.findCompletedByUserId(userId)).thenReturn(completed);
		List<SessionScoreSnapshot> snapshots = IntStream.rangeClosed(1, 11)
				.mapToObj(index -> snapshot("session_" + index, index))
				.toList();
		when(evaluations.findScoreSnapshotsBySessionIds(any()))
				.thenReturn(snapshots);
		ProfileInsightsServiceImpl service = service(
				goals,
				sessions,
				evaluations);

		var insights = service.getInsights(userId.toString());
		var trends = insights.abilityTrends();

		assertEquals(10, trends.size());
		assertEquals("session_2", trends.getFirst().sessionId());
		assertEquals(new BigDecimal("2"), trends.getFirst().scores().accuracy());
		assertEquals("2026-08-03T09:02+08:00",
				trends.getFirst().completedAt().toString());
		assertEquals("session_11", trends.getLast().sessionId());
		assertEquals("CUSTOM_SCENE", trends.getLast().trainingType());
		assertTrue(insights.weaknessAnalysis().reliable());
		assertEquals(10, insights.weaknessAnalysis().sampleCount());
		assertEquals("accuracy", insights.weaknesses().getFirst().dimension());
		assertEquals("CUSTOM_SCENE",
				insights.recommendations().getFirst().trainingType());
	}

	private ProfileInsightsServiceImpl service(
			WeeklyLearningGoalRepository goals,
			PracticeSessionRepository sessions,
			SessionEvaluationRepository evaluations) {
		return new ProfileInsightsServiceImpl(
				goals,
				sessions,
				evaluations,
				ZONE_ID,
				Clock.fixed(NOW, ZONE_ID));
	}

	private PracticeSessionRecord record(
			UUID userId,
			String sessionId,
			Instant endedAt,
			SceneType type) {
		return new PracticeSessionRecord(
				sessionId,
				userId,
				type.sceneIdPrefix() + "_scene",
				type,
				SessionStatus.COMPLETED,
				endedAt.minusSeconds(300),
				endedAt);
	}

	private SessionScoreSnapshot snapshot(String sessionId, int score) {
		BigDecimal value = BigDecimal.valueOf(score);
		return new SessionScoreSnapshot(
				sessionId,
				value,
				value,
				value,
				value,
				value);
	}

	private PracticeSessionRecord record(UUID userId, long durationSeconds) {
		Instant end = Instant.parse("2026-08-04T01:05:00Z");
		return new PracticeSessionRecord(
				"freechat_session1",
				userId,
				"freechat_scene",
				SceneType.FREE_CHAT,
				SessionStatus.COMPLETED,
				end.minusSeconds(durationSeconds),
				end);
	}
}
