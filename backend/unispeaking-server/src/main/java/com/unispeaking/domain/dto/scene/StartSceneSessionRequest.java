package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.scene.SceneType;

public record StartSceneSessionRequest(
		String userId,
		SceneType sceneType,
		String sceneInput,
		String userPreference,
		String offerSdp,
		ProviderType provider,
		String model,
		String voice,
		Boolean translationEnabled) {
}
