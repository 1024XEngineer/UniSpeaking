package com.unispeaking.infrastructure.persistence.repository.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.domain.po.auth.UserRole;
import com.unispeaking.domain.po.auth.UserStatus;
import com.unispeaking.infrastructure.persistence.entity.user.UserAccountEntity;
import com.unispeaking.infrastructure.persistence.mapper.user.UserAccountMapper;
import com.unispeaking.infrastructure.persistence.repository.user.UserAccountRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

	private UserAccountEntity toEntity(UserAccount user) {
		UserAccountEntity entity = new UserAccountEntity();
		entity.setId(user.id());
		entity.setUsername(user.username());
		entity.setPasswordHash(user.passwordHash());
		entity.setNickname(user.nickname());
		entity.setRole(user.role().name());
		entity.setStatus(user.status().name());
		entity.setAuthVersion(user.authVersion());
		entity.setLastLoginAt(toOffsetDateTime(user.lastLoginAt()));
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
				UserRole.valueOf(entity.getRole()),
				UserStatus.valueOf(entity.getStatus()),
				entity.getAuthVersion(),
				toInstant(entity.getLastLoginAt()),
				toInstant(entity.getCreatedAt()),
				toInstant(entity.getUpdatedAt()));
	}

	private OffsetDateTime toOffsetDateTime(Instant value) {
		return value == null ? null : value.atOffset(ZoneOffset.UTC);
	}

	private Instant toInstant(OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}
}
