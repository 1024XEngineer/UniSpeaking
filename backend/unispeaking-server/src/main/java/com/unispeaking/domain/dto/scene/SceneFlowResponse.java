package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.SceneFlowStage;

public record SceneFlowResponse(
		String sceneId,
		SceneFlowStage stage,
		Boolean completed) {
}
