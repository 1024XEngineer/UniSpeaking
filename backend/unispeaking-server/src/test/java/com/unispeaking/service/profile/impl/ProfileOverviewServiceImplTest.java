package com.unispeaking.service.profile.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.domain.po.auth.UserRole;
import com.unispeaking.domain.po.auth.UserStatus;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.config.ObjectStorageProperties;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.user.UserAccountRepository;
import com.unispeaking.infrastructure.storage.ObjectStorageProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileOverviewServiceImplTest {

	@Test
	void returnsRealPracticeAssetsAndCheckInStreak() {
		ZoneId zoneId = ZoneId.of("Asia/Shanghai");
		Instant now = Instant.parse("2026-08-03T12:00:00Z");
		UUID userId = UUID.randomUUID();
		UserAccountRepository accounts = mock(UserAccountRepository.class);
		SceneRepository scenes = mock(SceneRepository.class);
		SessionEvaluationRepository evaluations =
				mock(SessionEvaluationRepository.class);
		PracticeSessionRepository practiceSessions =
				mock(PracticeSessionRepository.class);
		ObjectStorageProvider storage = mock(ObjectStorageProvider.class);
		when(accounts.findById(userId)).thenReturn(Optional.of(new UserAccount(
				userId,
				"learner@example.com",
				"hash",
				"学习者",
				UserRole.USER,
				UserStatus.ACTIVE,
				0,
				null,
				now,
				now)));
		when(scenes.findAllIdsByUserId(userId.toString()))
				.thenReturn(List.of("custom_scene1"));
		when(scenes.countActiveByUserId(userId.toString())).thenReturn(4L);
		List<OffsetDateTime> reports = List.of(
				OffsetDateTime.parse("2026-08-03T02:00:00Z"),
				OffsetDateTime.parse("2026-08-02T02:00:00Z"),
				OffsetDateTime.parse("2026-08-01T02:00:00Z"));
		when(evaluations.findCreatedAtBySceneIdsBetween(any(), any(), any()))
				.thenReturn(reports, reports);
		when(practiceSessions.findCompletedOverlapping(any(), any(), any()))
				.thenReturn(List.of(new PracticeSessionRecord(
						"custom_session1",
						userId,
						"custom_scene1",
						SceneType.CUSTOM_SCENE,
						SessionStatus.COMPLETED,
						Instant.parse("2026-08-03T01:00:00Z"),
						Instant.parse("2026-08-03T01:05:00Z"))));
		when(storage.available()).thenReturn(false);
		ProfileOverviewServiceImpl service = new ProfileOverviewServiceImpl(
				accounts,
				scenes,
				evaluations,
				practiceSessions,
				storage,
				new ObjectStorageProperties(),
				zoneId,
				Clock.fixed(now, zoneId));

		var overview = service.getOverview(userId.toString(), "2026-08");

		assertEquals(300, overview.statistics().weeklyPracticeSeconds());
		assertEquals(4, overview.statistics().trainingRecordCount());
		assertEquals(3, overview.statistics().consecutiveLearningDays());
		assertEquals(7, overview.statistics().lastSevenDays().size());
		assertEquals(300,
				overview.statistics().lastSevenDays().getLast().practiceSeconds());
	}
}
