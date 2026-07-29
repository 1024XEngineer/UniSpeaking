package com.unispeaking.orchestration;

import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.scene.StartSceneSessionRequest;
import com.unispeaking.domain.dto.scene.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.scene.SceneService;
import org.springframework.stereotype.Component;

@Component
public class SceneSessionCoordinator {

	private final SceneService sceneService;
	private final SceneFlowServiceSelector sceneFlowServiceSelector;
	private final SessionServiceSelector sessionServiceSelector;
	private final RealtimeSessionConnector realtimeSessionConnector;

	public SceneSessionCoordinator(
			SceneService sceneService,
			SceneFlowServiceSelector sceneFlowServiceSelector,
			SessionServiceSelector sessionServiceSelector,
			RealtimeSessionConnector realtimeSessionConnector) {
		this.sceneService = sceneService;
		this.sceneFlowServiceSelector = sceneFlowServiceSelector;
		this.sessionServiceSelector = sessionServiceSelector;
		this.realtimeSessionConnector = realtimeSessionConnector;
	}

	public StartSceneSessionResponse start(StartSceneSessionRequest request) {
		SceneGenerationResponse scene = sceneService.generateScene(new SceneGenerationRequest(
				request.userId(),
				request.userPreference(),
				request.sceneType(),
				request.sceneInput()));
		SceneFlowResponse flow = sceneFlowServiceSelector.createFlow(scene.sceneId());
		SceneType sceneType = request.sceneType() == null
				? SceneType.FREE_CHAT
				: request.sceneType();
		String finalPrompt = scene.scenePrompt();
		StartSessionResponse session = sessionServiceSelector.startSession(
				sceneType,
				finalPrompt);
		RealtimeSessionConnection connection = realtimeSessionConnector.connect(
				session.sessionId(),
				scene.sceneId(),
				finalPrompt,
				request);
		return new StartSceneSessionResponse(
				scene.sceneId(),
				sceneName(sceneType, request.sceneInput()),
				sceneType,
				scene.wordList(),
				scene.phraseList(),
				scene.sentenceList(),
				flow.stage(),
				scoringEnabled(sceneType),
				session.sessionId(),
				connection.providerSessionId(),
				connection.answerSdp(),
				connection.credentialExpiresAt(),
				connection.voiceId(),
				connection.status(),
				session.startTime(),
				finalPrompt);
	}

	private boolean scoringEnabled(SceneType sceneType) {
		return sceneType != SceneType.FREE_CHAT;
	}

	private String sceneName(SceneType sceneType, String sceneInput) {
		if (sceneType == SceneType.FREE_CHAT) {
			return "Free Chat";
		}
		String name = sceneInput == null || sceneInput.isBlank()
				? "Custom Scene"
				: sceneInput.trim();
		return name.length() <= 24 ? name : name.substring(0, 24) + "...";
	}
}
