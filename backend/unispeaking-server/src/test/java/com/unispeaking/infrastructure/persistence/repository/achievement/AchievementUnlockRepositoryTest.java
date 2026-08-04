package com.unispeaking.infrastructure.persistence.repository.achievement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.achievement.UserAchievementState;
import com.unispeaking.domain.po.achievement.UserAchievementUnlock;
import com.unispeaking.infrastructure.persistence.entity.achievement.UserAchievementStateEntity;
import com.unispeaking.infrastructure.persistence.entity.achievement.UserAchievementUnlockEntity;
import com.unispeaking.infrastructure.persistence.mapper.achievement.UserAchievementStateMapper;
import com.unispeaking.infrastructure.persistence.mapper.achievement.UserAchievementUnlockMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class AchievementUnlockRepositoryTest {

	private UserAchievementUnlockMapper unlockMapper;
	private UserAchievementStateMapper stateMapper;
	private AchievementUnlockRepository repository;
	private UUID userId;
	private Instant unlockedAt;

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
				UserAchievementUnlockEntity.class);
	}

	@BeforeEach
	void setUp() {
		unlockMapper = mock(UserAchievementUnlockMapper.class);
		stateMapper = mock(UserAchievementStateMapper.class);
		repository = new AchievementUnlockRepository(unlockMapper, stateMapper);
		userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		unlockedAt = Instant.parse("2026-08-04T02:00:00Z");
	}

	@Test
	void createsInitializationStateAndReturnsConcurrentExistingState() {
		UserAchievementState state = new UserAchievementState(userId, unlockedAt);
		when(stateMapper.insert(any(UserAchievementStateEntity.class))).thenReturn(1);

		assertEquals(state, repository.initialize(state));

		UserAchievementStateEntity existing = stateEntity();
		when(stateMapper.insert(any(UserAchievementStateEntity.class)))
				.thenThrow(new DuplicateKeyException("duplicate"));
		when(stateMapper.selectById(userId)).thenReturn(existing);

		assertEquals(state, repository.initialize(state));
	}

	@Test
	void createsUnlockAndReturnsConcurrentExistingUnlock() {
		UserAchievementUnlock unlock = pendingUnlock();
		when(unlockMapper.insert(any(UserAchievementUnlockEntity.class))).thenReturn(1);

		assertEquals(unlock, repository.create(unlock));

		when(unlockMapper.insert(any(UserAchievementUnlockEntity.class)))
				.thenThrow(new DuplicateKeyException("duplicate"));
		when(unlockMapper.selectOne(any())).thenReturn(unlockEntity(null));

		assertEquals(unlock, repository.create(unlock));
	}

	@Test
	void listsPendingUnlocksInMapperOrder() {
		UserAchievementUnlockEntity first = unlockEntity(null);
		UserAchievementUnlockEntity second = unlockEntity(null);
		second.setAchievementId("conversation-2");
		second.setUnlockedAt(first.getUnlockedAt().plusSeconds(10));
		when(unlockMapper.selectList(any())).thenReturn(List.of(first, second));

		List<UserAchievementUnlock> pending = repository.findPending(userId);

		assertEquals(List.of("conversation-1", "conversation-2"), pending.stream()
				.map(UserAchievementUnlock::achievementId)
				.toList());
		assertTrue(pending.stream().allMatch(
				UserAchievementUnlock::pendingNotification));
	}

	@Test
	void acknowledgesPendingUnlockAndKeepsFirstAcknowledgement() {
		Instant acknowledgedAt = unlockedAt.plusSeconds(5);
		when(unlockMapper.selectOne(any()))
				.thenReturn(unlockEntity(null));
		when(unlockMapper.update(any(), any())).thenReturn(1);

		UserAchievementUnlock acknowledged = repository.acknowledge(
				userId, " conversation-1 ", acknowledgedAt);

		assertEquals(acknowledgedAt, acknowledged.acknowledgedAt());
		assertFalse(acknowledged.pendingNotification());

		UserAchievementUnlockEntity existing = unlockEntity(acknowledgedAt);
		when(unlockMapper.selectOne(any())).thenReturn(existing);
		UserAchievementUnlock repeated = repository.acknowledge(
				userId, "conversation-1", acknowledgedAt.plusSeconds(10));

		assertEquals(acknowledgedAt, repeated.acknowledgedAt());
		verify(unlockMapper).update(any(), any());
	}

	@Test
	void rejectsMissingUnlockWithoutWriting() {
		when(unlockMapper.selectOne(any())).thenReturn(null);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> repository.acknowledge(
						userId, "conversation-1", unlockedAt.plusSeconds(5)));

		assertEquals("ACHIEVEMENT_UNLOCK_NOT_FOUND", exception.code());
		verify(unlockMapper, never()).update(any(), any());
	}

	private UserAchievementUnlock pendingUnlock() {
		return new UserAchievementUnlock(
				userId,
				"conversation-1",
				unlockedAt,
				null);
	}

	private UserAchievementUnlockEntity unlockEntity(Instant acknowledgedAt) {
		UserAchievementUnlockEntity entity = new UserAchievementUnlockEntity();
		entity.setUserId(userId);
		entity.setAchievementId("conversation-1");
		entity.setUnlockedAt(unlockedAt.atOffset(ZoneOffset.UTC));
		entity.setAcknowledgedAt(acknowledgedAt == null
				? null
				: acknowledgedAt.atOffset(ZoneOffset.UTC));
		return entity;
	}

	private UserAchievementStateEntity stateEntity() {
		UserAchievementStateEntity entity = new UserAchievementStateEntity();
		entity.setUserId(userId);
		entity.setInitializedAt(unlockedAt.atOffset(ZoneOffset.UTC));
		return entity;
	}
}
