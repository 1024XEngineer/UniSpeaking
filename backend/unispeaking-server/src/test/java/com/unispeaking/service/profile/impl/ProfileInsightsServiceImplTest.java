package com.unispeaking.service.profile.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.profile.UpdateWeeklyLearningGoalsRequest;
import com.unispeaking.domain.po.profile.WeeklyLearningGoals;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.user.WeeklyLearningGoalRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
		when(goals.findByUserId(userId)).thenReturn(Optional.empty());
		when(sessions.findCompletedOverlapping(any(), any(), any()))
				.thenReturn(List.of(record(userId, 300)));
		ProfileInsightsServiceImpl service = service(goals, sessions);

		var response = service.getInsights(userId.toString()).weeklyGoals();

		assertEquals(120, response.durationTargetMinutes());
		assertEquals(300, response.completedDurationSeconds());
		assertEquals(5, response.trainingCountTarget());
		assertEquals(1, response.completedTrainingCount());
		assertEquals("2026-08-03T00:00+08:00", response.weekStartsAt().toString());
	}

	@Test
	void savesTargetsAndReturnsUpdatedProgress() {
		UUID userId = UUID.randomUUID();
		WeeklyLearningGoalRepository goals =
				mock(WeeklyLearningGoalRepository.class);
		PracticeSessionRepository sessions =
				mock(PracticeSessionRepository.class);
		WeeklyLearningGoals updated = new WeeklyLearningGoals(180, 6);
		when(goals.findByUserId(userId)).thenReturn(Optional.of(updated));
		when(sessions.findCompletedOverlapping(any(), any(), any()))
				.thenReturn(List.of());
		ProfileInsightsServiceImpl service = service(goals, sessions);

		var response = service.updateGoals(
				userId.toString(),
				new UpdateWeeklyLearningGoalsRequest(180, 6));

		verify(goals).save(userId, updated);
		assertEquals(180,
				response.weeklyGoals().durationTargetMinutes());
		assertEquals(6,
				response.weeklyGoals().trainingCountTarget());
	}

	private ProfileInsightsServiceImpl service(
			WeeklyLearningGoalRepository goals,
			PracticeSessionRepository sessions) {
		return new ProfileInsightsServiceImpl(
				goals,
				sessions,
				ZONE_ID,
				Clock.fixed(NOW, ZONE_ID));
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
