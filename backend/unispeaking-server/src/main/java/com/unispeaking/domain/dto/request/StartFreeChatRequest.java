package com.unispeaking.domain.dto.request;

import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.scene.SceneType;

public record StartFreeChatRequest(
		String userId,
		SceneType sceneType,
		String prompt,
		String userPreference,
		String offerSdp,
		String topic,
		ProviderType provider,
		String model,
		String voice,
		Boolean translationEnabled) {
}
