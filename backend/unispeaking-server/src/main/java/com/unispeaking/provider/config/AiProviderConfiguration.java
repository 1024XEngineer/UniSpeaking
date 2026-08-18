package com.unispeaking.provider.config;

public record AiProviderConfiguration(
		String providerId,
		String displayName,
		String adapterType,
		String baseUrl,
		boolean enabled,
		int connectTimeoutMs,
		int readTimeoutMs,
		long configVersion) {
}
