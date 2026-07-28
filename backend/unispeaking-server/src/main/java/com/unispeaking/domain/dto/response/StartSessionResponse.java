package com.unispeaking.domain.dto.response;

import com.unispeaking.domain.vo.session.SessionStatus;
import java.time.Instant;

public record StartSessionResponse(
		String localSessionId,
		String providerSessionId,
		String answerSdp,
		Instant credentialExpiresAt,
		String systemPrompt,
		String voiceId,
		SessionStatus status,
		Instant createdAt,
		Instant endedAt) {
}
