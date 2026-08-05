package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import java.util.List;

public interface SceneFlowService {
	SceneFlowResponse createFlow(String sceneId);
	SceneFlowResponse getFlow(String sceneId);
	SceneFlowResponse advanceStage(String sceneId, SceneFlowStage stage);
	void completeFlow(String sceneId, Boolean completed);
	List<LearningContentItem> getByCurrentStage(String sceneId, SceneFlowStage stage);
}
