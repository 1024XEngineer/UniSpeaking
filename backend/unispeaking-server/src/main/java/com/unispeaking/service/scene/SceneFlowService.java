package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.AdvanceSceneStageRequest;
import com.unispeaking.domain.dto.scene.CompleteSceneFlowRequest;
import com.unispeaking.domain.dto.scene.CreateSceneFlowRequest;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;

public interface SceneFlowService {
	SceneFlowResponse createFlow(CreateSceneFlowRequest request);
	SceneFlowResponse advanceStage(AdvanceSceneStageRequest request);
	SceneFlowResponse getFlow(String flowId);
	void completeFlow(CompleteSceneFlowRequest request);
}
