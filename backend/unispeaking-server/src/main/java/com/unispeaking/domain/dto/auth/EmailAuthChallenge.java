package com.unispeaking.domain.dto.auth;

import java.util.UUID;

public record EmailAuthChallenge(
		UUID challengeId,
		int expiresInSeconds,
		int resendAfterSeconds) {
}
