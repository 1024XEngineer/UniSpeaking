package com.unispeaking.domain.vo.session;

import com.unispeaking.domain.vo.provider.ProviderType;
import java.time.Instant;

public record RealtimeConnectionResult(
		String providerSessionId,
		ProviderType providerType,
		String modelId,
		String voiceId,
		String traceId,
		String answerSdp,
		Instant credentialExpiresAt) {

	public RealtimeConnectionResult(
			String providerSessionId,
			String answerSdp,
			Instant credentialExpiresAt) {
		this(providerSessionId, null, null, null, null, answerSdp, credentialExpiresAt);
	}
}
