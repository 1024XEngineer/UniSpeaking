package com.unispeaking.domain.po.auth;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
		UUID id,
		String username,
		String passwordHash,
		String nickname,
		String avatarObjectKey,
		UserRole role,
		UserStatus status,
		long authVersion,
		Instant lastLoginAt,
		Instant createdAt,
		Instant updatedAt) {

	public UserAccount(
			UUID id,
			String username,
			String passwordHash,
			String nickname,
			UserRole role,
			UserStatus status,
			long authVersion,
			Instant lastLoginAt,
			Instant createdAt,
			Instant updatedAt) {
		this(id, username, passwordHash, nickname, null, role, status,
				authVersion, lastLoginAt, createdAt, updatedAt);
	}

	public UserAccount withLastLoginAt(Instant value) {
		return new UserAccount(
				id,
				username,
				passwordHash,
				nickname,
				avatarObjectKey,
				role,
				status,
				authVersion,
				value,
				createdAt,
				updatedAt);
	}

	public UserAccount withNickname(String value) {
		return new UserAccount(id, username, passwordHash, value, avatarObjectKey,
				role, status, authVersion, lastLoginAt, createdAt, updatedAt);
	}

	public UserAccount withAvatarObjectKey(String value) {
		return new UserAccount(id, username, passwordHash, nickname, value,
				role, status, authVersion, lastLoginAt, createdAt, updatedAt);
	}

	public UserAccount withPasswordHashAndAuthVersion(String value, long version) {
		return new UserAccount(id, username, value, nickname, avatarObjectKey,
				role, status, version, lastLoginAt, createdAt, updatedAt);
	}
}
