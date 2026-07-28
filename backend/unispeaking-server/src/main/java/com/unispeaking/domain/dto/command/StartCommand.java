package com.unispeaking.domain.dto.command;

import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.scene.SceneType;

public record StartCommand(
		SceneType sceneType,
		String userId,
		String sceneId,
		String offerSdp,
		String topic,
		ProviderType provider,
		String model,
		String voice,
		Boolean translationEnabled) {
}
