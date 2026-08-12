package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.vo.scene.SceneType;

public record RealtimeConnectCommand(
		String modelId,
		String offerSdp,
		String userId,
		String clientId,
		String sceneId,
		SceneType sceneType,
		String voiceId) {
}
