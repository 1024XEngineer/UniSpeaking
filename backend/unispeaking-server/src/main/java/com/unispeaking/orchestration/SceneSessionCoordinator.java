package com.unispeaking.orchestration;

import com.unispeaking.domain.dto.scene.AdvanceSceneStageRequest;
import com.unispeaking.domain.dto.scene.CreateSceneFlowRequest;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.scene.StartSceneSessionRequest;
import com.unispeaking.domain.dto.scene.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionRequest;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.scene.SceneFlowService;
import com.unispeaking.service.scene.SceneService;
import org.springframework.stereotype.Component;

@Component
public class SceneSessionCoordinator {

	private final SceneService sceneService;
	private final SceneFlowService sceneFlowService;
	private final SessionServiceSelector sessionServiceSelector;

	public SceneSessionCoordinator(
			SceneService sceneService,
			SceneFlowService sceneFlowService,
			SessionServiceSelector sessionServiceSelector) {
		this.sceneService = sceneService;
		this.sceneFlowService = sceneFlowService;
		this.sessionServiceSelector = sessionServiceSelector;
	}

	public StartSceneSessionResponse start(StartSceneSessionRequest request) {
		SceneGenerationResponse scene = sceneService.generateScene(new SceneGenerationRequest(
				request.userId(),
				request.userPreference(),
				request.sceneType(),
				request.sceneInput()));
		SceneFlowResponse flow = sceneFlowService.createFlow(new CreateSceneFlowRequest(
				request.userId(),
				scene.sceneId()));
		if (scene.sceneType() == SceneType.FREE_CHAT) {
			flow = advanceToDialogue(request.userId(), flow.flowId());
		}
		StartSessionResponse session = sessionServiceSelector.startSession(new StartSessionRequest(
				request.userId(),
				scene.sceneId(),
				flow.flowId(),
				scene.sceneType(),
				scene.scenePrompt(),
				request.offerSdp(),
				request.provider(),
				request.model(),
				request.voice(),
				request.translationEnabled()));
		return new StartSceneSessionResponse(
				scene.sceneId(),
				scene.sceneName(),
				scene.sceneType(),
				scene.wordList(),
				scene.phraseList(),
				scene.sentenceList(),
				flow.flowId(),
				flow.currentStage(),
				scoringEnabled(scene.sceneType()),
				session.sessionId(),
				session.sessionId(),
				session.providerSessionId(),
				session.answerSdp(),
				session.credentialExpiresAt(),
				session.voiceId(),
				session.status(),
				session.startTime(),
				scene.scenePrompt(),
				scene.scenePrompt());
	}

	private SceneFlowResponse advanceToDialogue(String userId, String flowId) {
		SceneFlowResponse flow = sceneFlowService.getFlow(flowId);
		while (flow.currentStage() != SceneFlowStage.DIALOGUE) {
			flow = sceneFlowService.advanceStage(new AdvanceSceneStageRequest(userId, flowId));
		}
		return flow;
	}

	private boolean scoringEnabled(SceneType sceneType) {
		return sceneType != SceneType.FREE_CHAT;
	}
}
