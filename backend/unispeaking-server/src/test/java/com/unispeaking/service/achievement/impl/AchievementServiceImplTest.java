package com.unispeaking.service.achievement.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.achievement.AchievementAcknowledgeRequest;
import com.unispeaking.domain.dto.achievement.AchievementOverviewResponse;
import com.unispeaking.domain.dto.achievement.AchievementSyncResponse;
import com.unispeaking.domain.po.achievement.AchievementUnlockCreation;
import com.unispeaking.domain.po.achievement.UserAchievementState;
import com.unispeaking.domain.po.achievement.UserAchievementUnlock;
import com.unispeaking.domain.vo.achievement.AchievementMetricSnapshot;
import com.unispeaking.infrastructure.persistence.repository.achievement.AchievementUnlockRepository;
import com.unispeaking.component.achievement.AchievementCatalog;
import com.unispeaking.component.achievement.AchievementMetricCalculator;
import com.unispeaking.component.achievement.AchievementProgressCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AchievementServiceImplTest {

	private static final Instant UNLOCKED_AT =
			Instant.parse("2026-08-04T02:00:00Z");

	private UUID userId;
	private AchievementMetricCalculator metricCalculator;
	private AchievementUnlockRepository unlocks;
	private AchievementServiceImpl service;

	@BeforeEach
	void setUp() {
		userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		AchievementCatalog catalog = new AchievementCatalog();
		metricCalculator = mock(AchievementMetricCalculator.class);
		unlocks = mock(AchievementUnlockRepository.class);
		service = new AchievementServiceImpl(
				catalog,
				metricCalculator,
				new AchievementProgressCalculator(catalog),
				unlocks);
	}

	@Test
	void readsRealtimeValuesButUsesPersistedUnlockState() {
		when(metricCalculator.calculate(userId)).thenReturn(metrics(20));
		when(unlocks.findAll(userId)).thenReturn(List.of(
				unlock("conversation-1", UNLOCKED_AT),
				unlock("conversation-2", UNLOCKED_AT.plusSeconds(1))));

		AchievementOverviewResponse overview = service.getOverview(userId);

		var conversation = overview.series().getFirst();
		assertEquals(new BigDecimal("20"), conversation.currentValue());
		assertEquals(2, conversation.currentLevel());
		assertEquals("渐入佳境", conversation.currentTitle());
		assertEquals(3, conversation.nextLevel());
		assertFalse(conversation.milestones().get(2).unlocked());
		assertEquals(10, overview.series().size());
	}

	@Test
	void silentlyAcknowledgesHistoricalAchievementsOnFirstSynchronization() {
		when(metricCalculator.calculate(userId)).thenReturn(metrics(1));
		when(unlocks.findState(userId)).thenReturn(Optional.empty());
		when(unlocks.create(any(UserAchievementUnlock.class)))
				.thenAnswer(invocation -> new AchievementUnlockCreation(
						invocation.getArgument(0),
						true));
		when(unlocks.initialize(any(UserAchievementState.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(unlocks.findAll(userId)).thenReturn(List.of(
				unlock("conversation-1", UNLOCKED_AT)));

		AchievementSyncResponse response = service.synchronize(userId);

		assertTrue(response.initialized());
		assertTrue(response.newlyUnlocked().isEmpty());
		assertTrue(response.pendingNotifications().isEmpty());
		ArgumentCaptor<UserAchievementUnlock> created =
				ArgumentCaptor.forClass(UserAchievementUnlock.class);
		verify(unlocks).create(created.capture());
		assertEquals("conversation-1", created.getValue().achievementId());
		assertNotNull(created.getValue().acknowledgedAt());
		assertEquals(
				created.getValue().unlockedAt(),
				created.getValue().acknowledgedAt());
		verify(unlocks).initialize(any(UserAchievementState.class));
	}

	@Test
	void createsOnlyMissingNodesAndOrdersPendingNotificationsByCatalog() {
		UserAchievementUnlock first = unlock("conversation-1", UNLOCKED_AT);
		UserAchievementUnlock second = pending(
				"conversation-2",
				UNLOCKED_AT.plusSeconds(10));
		UserAchievementUnlock streak = pending(
				"streak-1",
				UNLOCKED_AT.plusSeconds(10));
		when(metricCalculator.calculate(userId)).thenReturn(metrics(5));
		when(unlocks.findState(userId)).thenReturn(Optional.of(
				new UserAchievementState(userId, UNLOCKED_AT)));
		when(unlocks.findAll(userId))
				.thenReturn(List.of(first), List.of(first, second));
		when(unlocks.create(any(UserAchievementUnlock.class)))
				.thenReturn(new AchievementUnlockCreation(second, true));
		when(unlocks.findPending(userId)).thenReturn(List.of(streak, second));

		AchievementSyncResponse response = service.synchronize(userId);

		assertEquals(
				List.of("conversation-2"),
				response.newlyUnlocked().stream()
						.map(item -> item.achievementId())
						.toList());
		assertEquals(
				List.of("conversation-2", "streak-1"),
				response.pendingNotifications().stream()
						.map(item -> item.achievementId())
						.toList());
		assertEquals(2, response.overview().series().getFirst().currentLevel());
	}

	@Test
	void excludesConcurrentDuplicateCreationFromNewlyUnlocked() {
		UserAchievementUnlock first = unlock("conversation-1", UNLOCKED_AT);
		UserAchievementUnlock concurrent = pending(
				"conversation-2",
				UNLOCKED_AT.plusSeconds(5));
		when(metricCalculator.calculate(userId)).thenReturn(metrics(5));
		when(unlocks.findState(userId)).thenReturn(Optional.of(
				new UserAchievementState(userId, UNLOCKED_AT)));
		when(unlocks.findAll(userId))
				.thenReturn(List.of(first), List.of(first, concurrent));
		when(unlocks.create(any(UserAchievementUnlock.class)))
				.thenReturn(new AchievementUnlockCreation(concurrent, false));
		when(unlocks.findPending(userId)).thenReturn(List.of(concurrent));

		AchievementSyncResponse response = service.synchronize(userId);

		assertTrue(response.newlyUnlocked().isEmpty());
		assertEquals(1, response.pendingNotifications().size());
	}

	@Test
	void recoversPartiallyPersistedUnlocksWithoutCreatingDuplicates() {
		UserAchievementUnlock first = unlock("conversation-1", UNLOCKED_AT);
		UserAchievementUnlock partiallyCreated = pending(
				"conversation-2",
				UNLOCKED_AT.plusSeconds(5));
		List<UserAchievementUnlock> existing =
				List.of(first, partiallyCreated);
		when(metricCalculator.calculate(userId)).thenReturn(metrics(5));
		when(unlocks.findState(userId)).thenReturn(Optional.of(
				new UserAchievementState(userId, UNLOCKED_AT)));
		when(unlocks.findAll(userId)).thenReturn(existing);
		when(unlocks.findPending(userId)).thenReturn(List.of(partiallyCreated));

		AchievementSyncResponse response = service.synchronize(userId);

		assertTrue(response.newlyUnlocked().isEmpty());
		assertEquals(
				List.of("conversation-2"),
				response.pendingNotifications().stream()
						.map(item -> item.achievementId())
						.toList());
		verify(unlocks, never()).create(any(UserAchievementUnlock.class));
	}

	@Test
	void validatesAndAcknowledgesOnlyCatalogAchievements() {
		BusinessException invalidRequest = assertThrows(
				BusinessException.class,
				() -> service.acknowledge(
						userId,
						"conversation-1",
						new AchievementAcknowledgeRequest(false)));
		assertEquals(
				"ACHIEVEMENT_ACKNOWLEDGEMENT_INVALID",
				invalidRequest.code());

		BusinessException missing = assertThrows(
				BusinessException.class,
				() -> service.acknowledge(
						userId,
						"unknown-1",
						new AchievementAcknowledgeRequest(true)));
		assertEquals("ACHIEVEMENT_UNLOCK_NOT_FOUND", missing.code());

		Instant acknowledgedAt = UNLOCKED_AT.plusSeconds(5);
		when(unlocks.acknowledge(
				eq(userId),
				eq("conversation-1"),
				any(Instant.class)))
				.thenReturn(new UserAchievementUnlock(
						userId,
						"conversation-1",
						UNLOCKED_AT,
						acknowledgedAt));

		var response = service.acknowledge(
				userId,
				" conversation-1 ",
				new AchievementAcknowledgeRequest(true));

		assertEquals("conversation-1", response.achievementId());
		assertEquals(acknowledgedAt, response.acknowledgedAt());
	}

	private AchievementMetricSnapshot metrics(long conversations) {
		return new AchievementMetricSnapshot(
				conversations,
				0,
				0,
				BigDecimal.ZERO,
				0,
				0,
				0,
				0,
				0,
				0);
	}

	private UserAchievementUnlock unlock(
			String achievementId,
			Instant unlockedAt) {
		return new UserAchievementUnlock(
				userId,
				achievementId,
				unlockedAt,
				unlockedAt);
	}

	private UserAchievementUnlock pending(
			String achievementId,
			Instant unlockedAt) {
		return new UserAchievementUnlock(
				userId,
				achievementId,
				unlockedAt,
				null);
	}
}
