package com.unispeaking.domain.vo.ai;

import com.unispeaking.domain.vo.realtime.ProviderType;

public record AiModelDefinition(
		String modelId,
		ProviderType providerType,
		AiCapability capability,
		boolean defaultModel) {
}
