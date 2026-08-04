package com.unispeaking.infrastructure.persistence.repository.achievement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.achievement.AchievementUnlockCreation;
import com.unispeaking.domain.po.achievement.UserAchievementState;
import com.unispeaking.domain.po.achievement.UserAchievementUnlock;
import com.unispeaking.infrastructure.persistence.entity.achievement.UserAchievementStateEntity;
import com.unispeaking.infrastructure.persistence.entity.achievement.UserAchievementUnlockEntity;
import com.unispeaking.infrastructure.persistence.mapper.achievement.UserAchievementStateMapper;
import com.unispeaking.infrastructure.persistence.mapper.achievement.UserAchievementUnlockMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class AchievementUnlockRepository {

	private final UserAchievementUnlockMapper unlockMapper;
	private final UserAchievementStateMapper stateMapper;

	public AchievementUnlockRepository(
			UserAchievementUnlockMapper unlockMapper,
			UserAchievementStateMapper stateMapper) {
		this.unlockMapper = unlockMapper;
		this.stateMapper = stateMapper;
	}

	public Optional<UserAchievementState> findState(UUID userId) {
		try {
			UserAchievementStateEntity entity = stateMapper.selectById(userId);
			return entity == null
					? Optional.empty()
					: Optional.of(toDomain(entity));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public UserAchievementState initialize(UserAchievementState state) {
		UserAchievementStateEntity entity = toEntity(state);
		OffsetDateTime now = atUtc(Instant.now());
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		try {
			if (stateMapper.insert(entity) == 1) {
				return state;
			}
			throw persistenceFailure();
		}
		catch (DuplicateKeyException exception) {
			return findState(state.userId()).orElseThrow(
					this::persistenceFailure);
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public AchievementUnlockCreation create(UserAchievementUnlock unlock) {
		UserAchievementUnlockEntity entity = toEntity(unlock);
		OffsetDateTime now = atUtc(Instant.now());
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		try {
			if (unlockMapper.insert(entity) == 1) {
				return new AchievementUnlockCreation(unlock, true);
			}
			throw persistenceFailure();
		}
		catch (DuplicateKeyException exception) {
			return new AchievementUnlockCreation(
					find(unlock.userId(), unlock.achievementId()).orElseThrow(
							this::persistenceFailure),
					false);
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<UserAchievementUnlock> find(
			UUID userId,
			String achievementId) {
		try {
			UserAchievementUnlockEntity entity = unlockMapper.selectOne(
					keyQuery(userId, normalizeId(achievementId)));
			return entity == null
					? Optional.empty()
					: Optional.of(toDomain(entity));
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<UserAchievementUnlock> findAll(UUID userId) {
		try {
			return unlockMapper.selectList(
						new LambdaQueryWrapper<UserAchievementUnlockEntity>()
								.eq(UserAchievementUnlockEntity::getUserId, userId)
								.orderByAsc(
										UserAchievementUnlockEntity::getUnlockedAt,
										UserAchievementUnlockEntity::getAchievementId))
					.stream()
					.map(this::toDomain)
					.toList();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<UserAchievementUnlock> findPending(UUID userId) {
		try {
			return unlockMapper.selectList(
						new LambdaQueryWrapper<UserAchievementUnlockEntity>()
								.eq(UserAchievementUnlockEntity::getUserId, userId)
								.isNull(UserAchievementUnlockEntity::getAcknowledgedAt)
								.orderByAsc(
										UserAchievementUnlockEntity::getUnlockedAt,
										UserAchievementUnlockEntity::getAchievementId))
					.stream()
					.map(this::toDomain)
					.toList();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public UserAchievementUnlock acknowledge(
			UUID userId,
			String achievementId,
			Instant acknowledgedAt) {
		String normalizedId = normalizeId(achievementId);
		UserAchievementUnlock existing = find(userId, normalizedId)
				.orElseThrow(this::unlockNotFound);
		if (!existing.pendingNotification()) {
			return existing;
		}
		if (acknowledgedAt.isBefore(existing.unlockedAt())) {
			throw persistenceFailure();
		}
		OffsetDateTime acknowledged = atUtc(acknowledgedAt);
		try {
			int updated = unlockMapper.update(
					null,
					new LambdaUpdateWrapper<UserAchievementUnlockEntity>()
							.eq(UserAchievementUnlockEntity::getUserId, userId)
							.eq(UserAchievementUnlockEntity::getAchievementId, normalizedId)
							.isNull(UserAchievementUnlockEntity::getAcknowledgedAt)
							.set(UserAchievementUnlockEntity::getAcknowledgedAt, acknowledged)
							.set(UserAchievementUnlockEntity::getUpdatedAt, acknowledged));
			if (updated == 1) {
				return new UserAchievementUnlock(
						userId,
						normalizedId,
						existing.unlockedAt(),
						acknowledgedAt);
			}
			return find(userId, normalizedId)
					.filter(unlock -> !unlock.pendingNotification())
					.orElseThrow(this::persistenceFailure);
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private LambdaQueryWrapper<UserAchievementUnlockEntity> keyQuery(
			UUID userId,
			String achievementId) {
		return new LambdaQueryWrapper<UserAchievementUnlockEntity>()
				.eq(UserAchievementUnlockEntity::getUserId, userId)
				.eq(UserAchievementUnlockEntity::getAchievementId, achievementId);
	}

	private String normalizeId(String achievementId) {
		if (achievementId == null || achievementId.isBlank()) {
			throw unlockNotFound();
		}
		return achievementId.trim();
	}

	private UserAchievementUnlockEntity toEntity(UserAchievementUnlock unlock) {
		UserAchievementUnlockEntity entity = new UserAchievementUnlockEntity();
		entity.setUserId(unlock.userId());
		entity.setAchievementId(unlock.achievementId());
		entity.setUnlockedAt(atUtc(unlock.unlockedAt()));
		entity.setAcknowledgedAt(unlock.acknowledgedAt() == null
				? null
				: atUtc(unlock.acknowledgedAt()));
		return entity;
	}

	private UserAchievementUnlock toDomain(UserAchievementUnlockEntity entity) {
		return new UserAchievementUnlock(
				entity.getUserId(),
				entity.getAchievementId(),
				entity.getUnlockedAt().toInstant(),
				entity.getAcknowledgedAt() == null
						? null
						: entity.getAcknowledgedAt().toInstant());
	}

	private UserAchievementStateEntity toEntity(UserAchievementState state) {
		UserAchievementStateEntity entity = new UserAchievementStateEntity();
		entity.setUserId(state.userId());
		entity.setInitializedAt(atUtc(state.initializedAt()));
		return entity;
	}

	private UserAchievementState toDomain(UserAchievementStateEntity entity) {
		return new UserAchievementState(
				entity.getUserId(),
				entity.getInitializedAt().toInstant());
	}

	private OffsetDateTime atUtc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private BusinessException unlockNotFound() {
		return new BusinessException(
				"ACHIEVEMENT_UNLOCK_NOT_FOUND",
				"成就尚未解锁");
	}

	private BusinessException persistenceFailure() {
		return new BusinessException(
				"ACHIEVEMENT_PERSISTENCE_FAILED",
				"成就状态保存失败");
	}
}
