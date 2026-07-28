package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.vo.session.SessionStatus;
import java.time.Instant;

public record StartSessionResponse(
		String sessionId,
		String sceneId,
		String flowId,
		String startTime,
		String providerSessionId,
		String answerSdp,
		Instant credentialExpiresAt,
		String voiceId,
		SessionStatus status) {
}
