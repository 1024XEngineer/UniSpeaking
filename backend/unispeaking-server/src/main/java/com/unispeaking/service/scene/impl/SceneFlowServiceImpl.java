package com.unispeaking.service.scene.impl;

import com.unispeaking.domain.dto.scene.AdvanceSceneStageRequest;
import com.unispeaking.domain.dto.scene.CompleteSceneFlowRequest;
import com.unispeaking.domain.dto.scene.CreateSceneFlowRequest;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.service.scene.SceneFlowService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SceneFlowServiceImpl implements SceneFlowService {

	private final Map<String, SceneFlowResponse> flows = new ConcurrentHashMap<>();

	@Override
	public SceneFlowResponse createFlow(CreateSceneFlowRequest request) {
		SceneFlowResponse response = new SceneFlowResponse(
				"flow_" + UUID.randomUUID(),
				request.sceneId(),
				SceneFlowStage.WORD_LEARNING,
				false);
		flows.put(response.flowId(), response);
		return response;
	}

	@Override
	public SceneFlowResponse advanceStage(AdvanceSceneStageRequest request) {
		SceneFlowResponse current = getFlow(request.flowId());
		SceneFlowStage nextStage = next(current.currentStage());
		SceneFlowResponse response = new SceneFlowResponse(
				current.flowId(),
				current.sceneId(),
				nextStage,
				nextStage == SceneFlowStage.COMPLETED);
		flows.put(response.flowId(), response);
		return response;
	}

	@Override
	public SceneFlowResponse getFlow(String flowId) {
		SceneFlowResponse flow = flows.get(flowId);
		if (flow == null) {
			throw new BusinessException("SCENE_FLOW_NOT_FOUND", "scene flow not found: " + flowId);
		}
		return flow;
	}

	@Override
	public void completeFlow(CompleteSceneFlowRequest request) {
		SceneFlowResponse current = getFlow(request.flowId());
		flows.put(current.flowId(), new SceneFlowResponse(
				current.flowId(),
				current.sceneId(),
				SceneFlowStage.COMPLETED,
				true));
	}

	private SceneFlowStage next(SceneFlowStage current) {
		return switch (current) {
			case WORD_LEARNING -> SceneFlowStage.PHRASE_LEARNING;
			case PHRASE_LEARNING -> SceneFlowStage.SENTENCE_LEARNING;
			case SENTENCE_LEARNING -> SceneFlowStage.DIALOGUE;
			case DIALOGUE, COMPLETED -> SceneFlowStage.COMPLETED;
		};
	}
}
