package com.unispeaking.infrastructure.persistence.repository.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.domain.po.auth.UserRole;
import com.unispeaking.domain.po.auth.UserStatus;
import com.unispeaking.infrastructure.persistence.entity.user.UserAccountEntity;
import com.unispeaking.infrastructure.persistence.mapper.user.UserAccountMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MybatisUserAccountRepositoryTest {

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
				UserAccountEntity.class);
	}

	@Test
	void mapsEntityToDomainById() {
		UserAccountMapper mapper = mock(UserAccountMapper.class);
		UUID id = UUID.randomUUID();
		UserAccountEntity entity = entity(id);
		when(mapper.selectById(id)).thenReturn(entity);
		MybatisUserAccountRepository repository =
				new MybatisUserAccountRepository(mapper);

		UserAccount account = repository.findById(id).orElseThrow();

		assertEquals(id, account.id());
		assertEquals("learner@example.com", account.username());
		assertEquals("avatar/object.png", account.avatarObjectKey());
		assertEquals(UserRole.USER, account.role());
		assertEquals(UserStatus.ACTIVE, account.status());
		assertEquals(entity.getCreatedAt().toInstant(), account.createdAt());
	}

	@Test
	void findsUsernameAndReturnsEmptyWhenMissing() {
		UserAccountMapper mapper = mock(UserAccountMapper.class);
		when(mapper.selectOne(any()))
				.thenReturn(entity(UUID.randomUUID()))
				.thenReturn(null);
		MybatisUserAccountRepository repository =
				new MybatisUserAccountRepository(mapper);

		assertTrue(repository.findByUsername("learner@example.com").isPresent());
		assertTrue(repository.findByUsername("missing@example.com").isEmpty());
	}

	@Test
	void mapsDomainFieldsWhenCreatingAccount() {
		UserAccountMapper mapper = mock(UserAccountMapper.class);
		when(mapper.insert(any(UserAccountEntity.class))).thenReturn(1);
		MybatisUserAccountRepository repository =
				new MybatisUserAccountRepository(mapper);
		UUID id = UUID.randomUUID();
		Instant now = Instant.parse("2026-08-03T01:02:03Z");
		UserAccount account = new UserAccount(
				id,
				"new@example.com",
				"hash",
				"新用户",
				null,
				UserRole.ADMIN,
				UserStatus.LOCKED,
				3,
				null,
				now,
				now);

		assertEquals(account, repository.create(account));

		ArgumentCaptor<UserAccountEntity> captor =
				ArgumentCaptor.forClass(UserAccountEntity.class);
		verify(mapper).insert(captor.capture());
		UserAccountEntity saved = captor.getValue();
		assertEquals(id, saved.getId());
		assertEquals("ADMIN", saved.getRole());
		assertEquals("LOCKED", saved.getStatus());
		assertEquals(3L, saved.getAuthVersion());
		assertNull(saved.getAvatarObjectKey());
		assertNull(saved.getLastLoginAt());
		assertEquals(now, saved.getCreatedAt().toInstant());
	}

	@Test
	void updatesLastLoginOnlyForExistingAccount() {
		UserAccountMapper mapper = mock(UserAccountMapper.class);
		UUID id = UUID.randomUUID();
		UserAccountEntity entity = entity(id);
		when(mapper.selectById(id)).thenReturn(entity);
		MybatisUserAccountRepository repository =
				new MybatisUserAccountRepository(mapper);
		Instant loginAt = Instant.parse("2026-08-03T03:00:00Z");

		repository.updateLastLoginAt(id, loginAt);

		assertEquals(loginAt, entity.getLastLoginAt().toInstant());
		verify(mapper).updateById(entity);

		UUID missingId = UUID.randomUUID();
		when(mapper.selectById(missingId)).thenReturn(null);
		repository.updateLastLoginAt(missingId, loginAt);
		verify(mapper, times(1)).updateById(any(UserAccountEntity.class));
	}

	@Test
	void returnsWhetherProfileAndPasswordUpdatesMatched() {
		UserAccountMapper mapper = mock(UserAccountMapper.class);
		when(mapper.update(isNull(), any(Wrapper.class)))
				.thenReturn(1, 0, 1, 0);
		MybatisUserAccountRepository repository =
				new MybatisUserAccountRepository(mapper);
		UUID id = UUID.randomUUID();

		assertTrue(repository.updateNickname(id, "新昵称"));
		assertFalse(repository.updateAvatarObjectKey(id, null, "new.png"));
		assertTrue(repository.updateAvatarObjectKey(id, "old.png", "new.png"));
		assertFalse(repository.updatePasswordAndAuthVersion(id, 2, "new-hash"));
	}

	private UserAccountEntity entity(UUID id) {
		OffsetDateTime now = OffsetDateTime.of(
				2026, 8, 3, 1, 2, 3, 0, ZoneOffset.UTC);
		UserAccountEntity entity = new UserAccountEntity();
		entity.setId(id);
		entity.setUsername("learner@example.com");
		entity.setPasswordHash("hash");
		entity.setNickname("学习者");
		entity.setAvatarObjectKey("avatar/object.png");
		entity.setRole("USER");
		entity.setStatus("ACTIVE");
		entity.setAuthVersion(2L);
		entity.setLastLoginAt(now);
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return entity;
	}
}
