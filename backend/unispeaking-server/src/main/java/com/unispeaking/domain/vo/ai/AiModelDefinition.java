package com.unispeaking.domain.vo.ai;

public record AiModelDefinition(
		String modelId,
		String providerId,
		AiCapability capability,
		boolean defaultModel) {
}
