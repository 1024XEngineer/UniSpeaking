package com.unispeaking.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unispeaking.domain.po.user.UserAccount;
import com.unispeaking.domain.po.user.UserRole;
import com.unispeaking.domain.po.user.UserStatus;
import com.unispeaking.infrastructure.persistence.mybatis.entity.UserAccountEntity;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.UserAccountMapper;
import com.unispeaking.repository.UserAccountRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisUserAccountRepository implements UserAccountRepository {

	private final UserAccountMapper mapper;

	public MybatisUserAccountRepository(UserAccountMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public Optional<UserAccount> findById(UUID id) {
		return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
	}

	@Override
	public Optional<UserAccount> findByUsername(String username) {
		UserAccountEntity entity = mapper.selectOne(Wrappers
				.<UserAccountEntity>lambdaQuery()
				.eq(UserAccountEntity::getUsername, username));
		return Optional.ofNullable(entity).map(this::toDomain);
	}

	@Override
	public UserAccount create(UserAccount user) {
		mapper.insert(toEntity(user));
		return user;
	}

	@Override
	public void updateLastLoginAt(UUID id, Instant lastLoginAt) {
		UserAccountEntity entity = mapper.selectById(id);
		if (entity == null) {
			return;
		}
		entity.setLastLoginAt(toOffsetDateTime(lastLoginAt));
		entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
		mapper.updateById(entity);
	}

	@Override
	public UserAccount updateProfile(UUID id, String nickname, String avatarObjectKey) {
		int updated = mapper.update(
				null,
				Wrappers.<UserAccountEntity>lambdaUpdate()
						.eq(UserAccountEntity::getId, id)
						.set(UserAccountEntity::getNickname, nickname)
						.set(UserAccountEntity::getAvatarObjectKey, avatarObjectKey)
						.set(UserAccountEntity::getUpdatedAt, now()));
		return requireUpdated(id, updated);
	}

	@Override
	public UserAccount updatePasswordAndAuthVersion(
			UUID id,
			String passwordHash,
			long authVersion) {
		int updated = mapper.update(
				null,
				Wrappers.<UserAccountEntity>lambdaUpdate()
						.eq(UserAccountEntity::getId, id)
						.set(UserAccountEntity::getPasswordHash, passwordHash)
						.set(UserAccountEntity::getAuthVersion, authVersion)
						.set(UserAccountEntity::getUpdatedAt, now()));
		return requireUpdated(id, updated);
	}

	@Override
	public UserAccount requestDeletion(
			UUID id,
			long authVersion,
			Instant requestedAt,
			Instant scheduledAt) {
		int updated = mapper.update(
				null,
				Wrappers.<UserAccountEntity>lambdaUpdate()
						.eq(UserAccountEntity::getId, id)
						.set(UserAccountEntity::getStatus, UserStatus.PENDING_DELETION.name())
						.set(UserAccountEntity::getAuthVersion, authVersion)
						.set(
								UserAccountEntity::getDeletionRequestedAt,
								toOffsetDateTime(requestedAt))
						.set(
								UserAccountEntity::getDeletionScheduledAt,
								toOffsetDateTime(scheduledAt))
						.set(UserAccountEntity::getUpdatedAt, now()));
		return requireUpdated(id, updated);
	}

	@Override
	public UserAccount reactivate(UUID id, long authVersion) {
		int updated = mapper.update(
				null,
				Wrappers.<UserAccountEntity>lambdaUpdate()
						.eq(UserAccountEntity::getId, id)
						.set(UserAccountEntity::getStatus, UserStatus.ACTIVE.name())
						.set(UserAccountEntity::getAuthVersion, authVersion)
						.set(UserAccountEntity::getDeletionRequestedAt, null)
						.set(UserAccountEntity::getDeletionScheduledAt, null)
						.set(UserAccountEntity::getUpdatedAt, now()));
		return requireUpdated(id, updated);
	}

	@Override
	public List<UserAccount> findDeletionDueBefore(Instant cutoff, int limit) {
		int boundedLimit = Math.max(1, Math.min(limit, 1_000));
		return mapper.selectList(Wrappers
						.<UserAccountEntity>lambdaQuery()
						.eq(UserAccountEntity::getStatus, UserStatus.PENDING_DELETION.name())
						.le(
								UserAccountEntity::getDeletionScheduledAt,
								toOffsetDateTime(cutoff))
						.orderByAsc(UserAccountEntity::getDeletionScheduledAt)
						.last("LIMIT " + boundedLimit))
				.stream()
				.map(this::toDomain)
				.toList();
	}

	@Override
	public void deleteById(UUID id) {
		mapper.deleteById(id);
	}

	private UserAccountEntity toEntity(UserAccount user) {
		UserAccountEntity entity = new UserAccountEntity();
		entity.setId(user.id());
		entity.setUsername(user.username());
		entity.setPasswordHash(user.passwordHash());
		entity.setNickname(user.nickname());
		entity.setAvatarObjectKey(user.avatarObjectKey());
		entity.setRole(user.role().name());
		entity.setStatus(user.status().name());
		entity.setAuthVersion(user.authVersion());
		entity.setLastLoginAt(toOffsetDateTime(user.lastLoginAt()));
		entity.setDeletionRequestedAt(toOffsetDateTime(user.deletionRequestedAt()));
		entity.setDeletionScheduledAt(toOffsetDateTime(user.deletionScheduledAt()));
		entity.setCreatedAt(toOffsetDateTime(user.createdAt()));
		entity.setUpdatedAt(toOffsetDateTime(user.updatedAt()));
		return entity;
	}

	private UserAccount toDomain(UserAccountEntity entity) {
		return new UserAccount(
				entity.getId(),
				entity.getUsername(),
				entity.getPasswordHash(),
				entity.getNickname(),
				entity.getAvatarObjectKey(),
				UserRole.valueOf(entity.getRole()),
				UserStatus.valueOf(entity.getStatus()),
				entity.getAuthVersion(),
				toInstant(entity.getLastLoginAt()),
				toInstant(entity.getDeletionRequestedAt()),
				toInstant(entity.getDeletionScheduledAt()),
				toInstant(entity.getCreatedAt()),
				toInstant(entity.getUpdatedAt()));
	}

	private UserAccount requireUpdated(UUID id, int updated) {
		if (updated != 1) {
			throw new IllegalStateException("User account update failed: " + id);
		}
		return findById(id)
				.orElseThrow(() -> new IllegalStateException("Updated user account not found: " + id));
	}

	private OffsetDateTime now() {
		return OffsetDateTime.now(ZoneOffset.UTC);
	}

	private OffsetDateTime toOffsetDateTime(Instant value) {
		return value == null ? null : value.atOffset(ZoneOffset.UTC);
	}

	private Instant toInstant(OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}
}
