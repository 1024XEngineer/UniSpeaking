package com.unispeaking.domain.dto.auth;

import java.time.Instant;

public record MobileAuthResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserAccountResponse user) {}
