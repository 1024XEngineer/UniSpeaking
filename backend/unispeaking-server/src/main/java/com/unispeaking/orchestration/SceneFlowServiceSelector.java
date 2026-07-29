package com.unispeaking.orchestration;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.service.scene.SceneFlowService;
import com.unispeaking.service.scene.impl.SceneFlowServiceImpl;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class SceneFlowServiceSelector {

	private final ObjectProvider<SceneFlowServiceImpl> flowServices;
	private final Map<String, SceneFlowService> flows = new ConcurrentHashMap<>();

	public SceneFlowServiceSelector(ObjectProvider<SceneFlowServiceImpl> flowServices) {
		this.flowServices = flowServices;
	}

	public SceneFlowResponse createFlow(String sceneId) {
		SceneFlowService service = flowServices.getObject();
		SceneFlowResponse response = service.createFlow(sceneId);
		flows.put(response.sceneId(), service);
		return response;
	}

	public SceneFlowResponse advanceStage(String sceneId, SceneFlowStage stage) {
		return resolve(sceneId).advanceStage(stage);
	}

	public void completeFlow(String sceneId, Boolean completed) {
		SceneFlowService service = resolve(sceneId);
		service.completeFlow(completed);
		if (Boolean.TRUE.equals(completed)) {
			flows.remove(sceneId, service);
		}
	}

	public List<LearningContentItem> getByCurrentStage(
			String sceneId,
			SceneFlowStage stage) {
		return resolve(sceneId).getByCurrentStage(stage);
	}

	private SceneFlowService resolve(String sceneId) {
		SceneFlowService service = flows.get(sceneId);
		if (service == null) {
			throw new BusinessException(
					"SCENE_FLOW_NOT_FOUND",
					"scene flow not found for scene: " + sceneId);
		}
		return service;
	}
}
