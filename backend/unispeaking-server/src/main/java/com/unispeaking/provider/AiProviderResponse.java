package com.unispeaking.provider;

public record AiProviderResponse<T>(T response, String providerRequestId, ProviderUsage usage) {
	public AiProviderResponse {
		usage = usage == null ? new ProviderUsage(0, 0, 0, 0, 0, 0, "NONE") : usage;
	}
}
