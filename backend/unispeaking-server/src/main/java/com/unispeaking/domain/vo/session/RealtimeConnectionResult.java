package com.unispeaking.domain.vo.session;

import java.time.Instant;

public record RealtimeConnectionResult(
		String providerSessionId,
		String answerSdp,
		Instant credentialExpiresAt) {
}
