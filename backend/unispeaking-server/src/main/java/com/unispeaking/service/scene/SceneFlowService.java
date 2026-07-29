package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import java.util.List;

public interface SceneFlowService {
	SceneFlowResponse createFlow(String sceneId);
	SceneFlowResponse advanceStage(SceneFlowStage stage);
	void completeFlow(Boolean completed);
	List<LearningContentItem> getByCurrentStage(SceneFlowStage stage);
}
