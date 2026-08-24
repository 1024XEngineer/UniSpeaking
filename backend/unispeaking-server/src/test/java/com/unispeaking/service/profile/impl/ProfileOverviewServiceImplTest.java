package com.unispeaking.service.profile.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.unispeaking.provider.ObjectStorageProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.unispeaking.common.exception.BusinessException;
import java.net.URI;

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

	@Test
	void rejectsFutureAndMalformedMonthsAndUnknownUsers() {
		UserAccountRepository accounts = mock(UserAccountRepository.class);
		UUID id = UUID.randomUUID();
		when(accounts.findById(id)).thenReturn(Optional.empty());
		ProfileOverviewServiceImpl service = new ProfileOverviewServiceImpl(accounts,
				mock(SceneRepository.class), mock(SessionEvaluationRepository.class),
				mock(PracticeSessionRepository.class), mock(ObjectStorageProvider.class),
				new ObjectStorageProperties(), ZoneId.of("UTC"), Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneId.of("UTC")));
		assertEquals("USER_NOT_FOUND", assertThrows(BusinessException.class,
				() -> service.getOverview(id.toString(), null)).code());

		when(accounts.findById(id)).thenReturn(Optional.of(new UserAccount(id, "user@example.com", "hash", null,
				UserRole.USER, UserStatus.ACTIVE, 0, null, Instant.now(), Instant.now())));
		assertEquals("PROFILE_MONTH_INVALID", assertThrows(BusinessException.class,
				() -> service.getOverview(id.toString(), "2026/08")).code());
		assertEquals("PROFILE_MONTH_INVALID", assertThrows(BusinessException.class,
				() -> service.getOverview(id.toString(), "2026-09")).code());
	}

	@Test
	void fallsBackToEmailNameAndSignsAvatarWhenStorageIsAvailable() {
		ZoneId zone = ZoneId.of("UTC");
		Instant now = Instant.parse("2026-08-03T00:00:00Z");
		UUID id = UUID.randomUUID();
		UserAccountRepository accounts = mock(UserAccountRepository.class);
		SceneRepository scenes = mock(SceneRepository.class);
		SessionEvaluationRepository evaluations = mock(SessionEvaluationRepository.class);
		PracticeSessionRepository sessions = mock(PracticeSessionRepository.class);
		ObjectStorageProvider storage = mock(ObjectStorageProvider.class);
		when(accounts.findById(id)).thenReturn(Optional.of(new UserAccount(id, "name@example.com", "hash", " ",
				"avatars/name.png", UserRole.USER, UserStatus.ACTIVE, 0, null, now, now)));
		when(scenes.findAllIdsByUserId(id.toString())).thenReturn(List.of());
		when(scenes.countActiveByUserId(id.toString())).thenReturn(0L);
		when(evaluations.findCreatedAtBySceneIdsBetween(any(), any(), any())).thenReturn(List.of());
		when(sessions.findCompletedOverlapping(any(), any(), any())).thenReturn(List.of());
		when(storage.available()).thenReturn(true);
		when(storage.signGetUrl(any(), any())).thenReturn(URI.create("https://cdn.example/avatar.png"));
		ProfileOverviewServiceImpl service = new ProfileOverviewServiceImpl(accounts, scenes, evaluations, sessions,
				storage, new ObjectStorageProperties(), zone, Clock.fixed(now, zone));
		var overview = service.getOverview(id.toString(), "2026-08");
		assertEquals("name", overview.account().displayName());
		assertEquals("https://cdn.example/avatar.png", overview.account().avatarUrl());
	}
}
