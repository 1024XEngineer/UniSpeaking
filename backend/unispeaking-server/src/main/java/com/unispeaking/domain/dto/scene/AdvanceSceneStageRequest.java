package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.SceneFlowStage;

public record AdvanceSceneStageRequest(
		String sceneId,
		SceneFlowStage stage) {
}
