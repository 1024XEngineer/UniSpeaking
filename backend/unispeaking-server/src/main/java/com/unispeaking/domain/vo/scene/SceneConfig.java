package com.unispeaking.domain.vo.scene;

import com.unispeaking.domain.vo.provider.ProviderType;

public record SceneConfig(
		SceneType type,
		ProviderType providerType,
		String model,
		String defaultVoice,
		boolean translationEnabled) {
}
