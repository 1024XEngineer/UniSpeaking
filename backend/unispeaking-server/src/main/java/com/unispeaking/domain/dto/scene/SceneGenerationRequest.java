package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.SceneType;

public record SceneGenerationRequest(
		String userId,
		String userPreference,
		SceneType sceneType,
		String sceneInput) {
}
