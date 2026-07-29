package com.unispeaking.domain.vo.auth;

import java.time.Instant;

public record IssuedJwt(String token, Instant expiresAt) {
}
