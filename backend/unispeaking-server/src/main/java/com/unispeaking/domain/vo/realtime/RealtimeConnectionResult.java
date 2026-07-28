package com.unispeaking.domain.vo.realtime;

import java.time.Instant;

public record RealtimeConnectionResult(
		String providerSessionId,
		String answerSdp,
		Instant credentialExpiresAt) {
}
