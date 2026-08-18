package com.unispeaking.provider.usage;

import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.provider.AiInvocationContext;
import com.unispeaking.provider.ProviderUsage;
import com.unispeaking.provider.config.AiModelConfiguration;
import java.time.Instant;
import java.util.UUID;

public record AiInvocationAttempt(
		UUID invocationId,
		AiInvocationContext context,
		int attemptNo,
		AiCapability capability,
		AiModelConfiguration model,
		String providerRequestId,
		Instant startedAt,
		Instant completedAt,
		long durationMs,
		ProviderUsage usage,
		String status,
		String errorCode,
		boolean retryable,
		String fallbackFromModelId) {

	public AiInvocationAttempt {
		invocationId = invocationId == null ? UUID.randomUUID() : invocationId;
		usage = usage == null ? new ProviderUsage(0, 0, 0, 0, 0, 0, "NONE") : usage;
	}
}
