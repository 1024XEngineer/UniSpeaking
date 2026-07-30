package com.unispeaking.domain.dto.auth;

import com.unispeaking.domain.po.user.UserAccount;
import java.time.Instant;
import java.util.UUID;

public record UserAccountResponse(
		UUID id,
		String username,
		String nickname,
		String avatarUrl,
		String role,
		String status,
		Instant lastLoginAt,
		Instant createdAt) {

	public static UserAccountResponse from(UserAccount user, String avatarUrl) {
		return new UserAccountResponse(
				user.id(),
				user.username(),
				user.nickname(),
				avatarUrl,
				user.role().name(),
				user.status().name(),
				user.lastLoginAt(),
				user.createdAt());
	}
}
