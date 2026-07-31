package com.unispeaking.domain.vo.provider;

public record AiModelDefinition(
		String modelId,
		String providerId,
		AiCapability capability,
		boolean defaultModel) {
}
