package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.vo.scene.SceneType;

public record StartSessionCommand(
		String userId,
		String sceneId,
		SceneType sceneType,
		String stage,
		String prompt) {
}
