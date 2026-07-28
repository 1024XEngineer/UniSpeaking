package com.unispeaking.domain.vo.scene;

import com.unispeaking.domain.vo.realtime.ProviderType;

public record SceneConfig(
		SceneType type,
		ProviderType providerType,
		String model,
		String defaultVoice,
		boolean translationEnabled) {
}
