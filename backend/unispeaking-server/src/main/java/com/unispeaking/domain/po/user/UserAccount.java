package com.unispeaking.domain.po.user;

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
		Instant deletionRequestedAt,
		Instant deletionScheduledAt,
		Instant createdAt,
		Instant updatedAt) {

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
				deletionRequestedAt,
				deletionScheduledAt,
				createdAt,
				updatedAt);
	}
}
