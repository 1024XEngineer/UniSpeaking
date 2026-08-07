package com.unispeaking.service.session.impl;

import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.dto.scene.FreeChatSceneRequest;
import com.unispeaking.domain.dto.scene.FreeChatSceneContext;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.StartFreeChatRequest;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.scene.FreeChatSceneService;
import com.unispeaking.service.session.FreeChatSessionService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Free-chat session orchestration belongs to the session module. The scene
 * service is used only to generate the immutable scene prompt.
 */
@Service
public class FreeChatSessionServiceImpl implements FreeChatSessionService {

	private final FreeChatSceneService sceneService;
	private final SessionLifecycleManager sessionLifecycle;
	private final RealtimeSessionCoordinator sessionCoordinator;

	public FreeChatSessionServiceImpl(
			FreeChatSceneService sceneService,
			SessionLifecycleManager sessionLifecycle,
			RealtimeSessionCoordinator sessionCoordinator) {
		this.sceneService = sceneService;
		this.sessionLifecycle = sessionLifecycle;
		this.sessionCoordinator = sessionCoordinator;
	}

	@Override
	public StartSceneSessionResponse startSession(StartFreeChatRequest request) {
		FreeChatSceneContext prepared = sceneService.prepare(
				new FreeChatSceneRequest(null));
		var generated = prepared.scene();
		SceneGenerationResponse scene = new SceneGenerationResponse(
				generated.sceneId(),
				List.of(),
				List.of(),
				List.of(),
				generated.dialoguePrompt());
		StartSessionResponse started = sessionLifecycle.startSession(
				new StartSessionCommand(
						prepared.userId(),
						generated.sceneId(),
						SceneType.FREE_CHAT,
						"DIALOGUE",
						generated.dialoguePrompt()));
		return sessionCoordinator.connect(
				scene,
				"Free Chat",
				SceneFlowStage.DIALOGUE,
				false,
				started,
				SceneType.FREE_CHAT,
				generated.sceneId(),
				generated.dialoguePrompt(),
				request.offerSdp(),
				request.provider(),
				request.model(),
				request.voice(),
				request.translationEnabled());
	}

	@Override
	public void addMessage(String sessionId, Message message) {
		sessionLifecycle.addMessage(sessionId, message);
	}

	@Override
	public Void endSession(String sessionId) {
		sessionLifecycle.endSession(sessionId);
		return null;
	}

}
