package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.SceneFlowStage;

public record SceneFlowResponse(
		String flowId,
		String sceneId,
		SceneFlowStage currentStage,
		Boolean completed) {
}
