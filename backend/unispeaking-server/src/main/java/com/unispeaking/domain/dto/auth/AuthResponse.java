package com.unispeaking.domain.dto.auth;

import java.time.Instant;

public record AuthResponse(
		String tokenType,
		String accessToken,
		Instant expiresAt,
		UserAccountResponse user) {
}
