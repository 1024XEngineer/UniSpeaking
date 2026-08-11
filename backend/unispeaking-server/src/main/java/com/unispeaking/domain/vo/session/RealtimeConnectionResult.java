package com.unispeaking.domain.vo.session;

import com.unispeaking.domain.vo.provider.ProviderType;
import java.time.Instant;

public record RealtimeConnectionResult(
		String providerSessionId,
		String answerSdp,
		Instant credentialExpiresAt,
		ProviderType providerType,
		String modelId,
		RealtimeTransportType transportType) {

	public RealtimeConnectionResult(
			String providerSessionId,
			String answerSdp,
			Instant credentialExpiresAt) {
		this(
				providerSessionId,
				answerSdp,
				credentialExpiresAt,
				null,
				null,
				RealtimeTransportType.PLATFORM_RTC);
	}
}
