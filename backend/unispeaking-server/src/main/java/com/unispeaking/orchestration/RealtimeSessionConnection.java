package com.unispeaking.orchestration;

import com.unispeaking.domain.vo.session.SessionStatus;
import java.time.Instant;

public record RealtimeSessionConnection(
		String providerSessionId,
		String answerSdp,
		Instant credentialExpiresAt,
		String voiceId,
		SessionStatus status) {
}
