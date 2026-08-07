package com.unispeaking.service.achievement;

import com.unispeaking.component.achievement.AchievementMetricCalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.po.achievement.AchievementEvaluationFact;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.achievement.AchievementMetricSnapshot;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AchievementMetricCalculatorTest {

	@Test
	void calculatesAllMetricsWithBusinessBoundariesAndShanghaiDates() {
		UUID userId = UUID.randomUUID();
		PracticeSessionRepository practiceSessions =
				mock(PracticeSessionRepository.class);
		SceneRepository scenes = mock(SceneRepository.class);
		SessionEvaluationRepository sessionEvaluations =
				mock(SessionEvaluationRepository.class);
		TurnEvaluationRepository turnEvaluations =
				mock(TurnEvaluationRepository.class);
		SceneSentenceReadingRepository sentenceReadings =
				mock(SceneSentenceReadingRepository.class);
		List<PracticeSessionRecord> completedSessions = List.of(
				session(userId, "short", "scene-short", 29),
				session(userId, "boundary", "scene-a", 30),
				session(userId, "long", "scene-a", 90),
				session(userId, "backwards", "scene-b", -10));
		List<String> ownedSceneIds = List.of("scene-a", "scene-old");
		when(practiceSessions.findCompletedByUserId(userId))
				.thenReturn(completedSessions);
		when(scenes.findAllIdsByUserId(userId.toString()))
				.thenReturn(ownedSceneIds);
		when(sessionEvaluations.findAchievementFacts(
				completedSessions.stream()
						.map(PracticeSessionRecord::sessionId)
						.toList(),
				ownedSceneIds))
				.thenReturn(List.of(
						fact("first", "2026-07-31T15:30:00Z", "80"),
						fact("second", "2026-07-31T16:30:00Z", "79"),
						fact("same-day", "2026-08-01T15:30:00Z", "92"),
						fact("third", "2026-08-01T16:30:00Z", null),
						fact("gap", "2026-08-03T16:30:00Z", "0")));
		when(turnEvaluations.findBestOverallScore(
				completedSessions.stream()
						.map(PracticeSessionRecord::sessionId)
						.toList(),
				ownedSceneIds))
				.thenReturn(java.util.Optional.of(new BigDecimal("108")));
		when(sentenceReadings.countAttemptsBySceneIds(ownedSceneIds))
				.thenReturn(12L);
		when(scenes.countAllByUserId(userId.toString())).thenReturn(7L);
		AchievementMetricCalculator calculator = new AchievementMetricCalculator(
				practiceSessions,
				scenes,
				sessionEvaluations,
				turnEvaluations,
				sentenceReadings);

		AchievementMetricSnapshot metrics = calculator.calculate(userId);

		assertEquals(2, metrics.completedConversationCount());
		assertEquals(3, metrics.longestStreakDays());
		assertEquals(1, metrics.distinctCompletedSceneCount());
		assertEquals(new BigDecimal("100"), metrics.bestExpressionScore());
		assertEquals(12, metrics.pronunciationAttemptCount());
		assertEquals(7, metrics.historicalAssetCount());
		assertEquals(3, metrics.bestMonthlyCheckinDays());
		assertEquals(120, metrics.validPracticeSeconds());
		assertEquals(4, metrics.activeDays());
		assertEquals(2, metrics.qualitySessionCount());
	}

	@Test
	void returnsZeroForDateAndScoreMetricsWithoutEvaluationFacts() {
		UUID userId = UUID.randomUUID();
		PracticeSessionRepository practiceSessions =
				mock(PracticeSessionRepository.class);
		SceneRepository scenes = mock(SceneRepository.class);
		SessionEvaluationRepository sessionEvaluations =
				mock(SessionEvaluationRepository.class);
		TurnEvaluationRepository turnEvaluations =
				mock(TurnEvaluationRepository.class);
		SceneSentenceReadingRepository sentenceReadings =
				mock(SceneSentenceReadingRepository.class);
		when(practiceSessions.findCompletedByUserId(userId)).thenReturn(List.of());
		when(scenes.findAllIdsByUserId(userId.toString())).thenReturn(List.of());
		when(sessionEvaluations.findAchievementFacts(List.of(), List.of()))
				.thenReturn(List.of());
		when(turnEvaluations.findBestOverallScore(List.of(), List.of()))
				.thenReturn(java.util.Optional.empty());
		AchievementMetricCalculator calculator = new AchievementMetricCalculator(
				practiceSessions,
				scenes,
				sessionEvaluations,
				turnEvaluations,
				sentenceReadings);

		AchievementMetricSnapshot metrics = calculator.calculate(userId);

		assertEquals(BigDecimal.ZERO, metrics.bestExpressionScore());
		assertEquals(0, metrics.longestStreakDays());
		assertEquals(0, metrics.bestMonthlyCheckinDays());
		assertEquals(0, metrics.activeDays());
	}

	private PracticeSessionRecord session(
			UUID userId,
			String sessionId,
			String sceneId,
			long seconds) {
		Instant startedAt = Instant.parse("2026-08-01T00:00:00Z");
		return new PracticeSessionRecord(
				sessionId,
				userId,
				sceneId,
				SceneType.CUSTOM_SCENE,
				SessionStatus.COMPLETED,
				startedAt,
				startedAt.plusSeconds(seconds));
	}

	private AchievementEvaluationFact fact(
			String sessionId,
			String createdAt,
			String finalScore) {
		return new AchievementEvaluationFact(
				sessionId,
				OffsetDateTime.parse(createdAt),
				finalScore == null ? null : new BigDecimal(finalScore));
	}
}
