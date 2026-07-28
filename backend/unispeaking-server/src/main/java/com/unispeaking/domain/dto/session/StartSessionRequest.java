package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.scene.SceneType;

public record StartSessionRequest(
		String userId,
		String sceneId,
		String flowId,
		SceneType sceneType,
		String prompt,
		String offerSdp,
		ProviderType provider,
		String model,
		String voice,
		Boolean translationEnabled) {
}
