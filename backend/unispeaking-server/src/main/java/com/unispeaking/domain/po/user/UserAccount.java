package com.unispeaking.domain.po.user;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
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

	public UserAccount withLastLoginAt(Instant value) {
		return new UserAccount(
				id,
				username,
				passwordHash,
				nickname,
				role,
				status,
				authVersion,
				value,
				createdAt,
				updatedAt);
	}
}
