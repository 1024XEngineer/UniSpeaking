package com.unispeaking.service.session.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.dto.scene.FreeChatSceneRequest;
import com.unispeaking.domain.dto.scene.FreeChatSceneContext;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.StartFreeChatRequest;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.SessionDetail;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.scene.impl.FreeChatSceneServiceImpl;
import com.unispeaking.service.session.SessionService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Free-chat session orchestration belongs to the session module. The scene
 * service is used only to generate the immutable scene prompt.
 */
@Service
public class FreeChatSessionServiceImpl implements SessionService {

	private final FreeChatSceneServiceImpl sceneService;
	private final SessionLifecycleManager sessionLifecycle;
	private final RealtimeSessionCoordinator sessionCoordinator;

	public FreeChatSessionServiceImpl(
			FreeChatSceneServiceImpl sceneService,
			SessionLifecycleManager sessionLifecycle,
			RealtimeSessionCoordinator sessionCoordinator) {
		this.sceneService = sceneService;
		this.sessionLifecycle = sessionLifecycle;
		this.sessionCoordinator = sessionCoordinator;
	}

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
		StartSessionResponse started = startSession(
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
	public StartSessionResponse startSession(StartSessionCommand command) {
		requireSceneType(command, SceneType.FREE_CHAT);
		return sessionLifecycle.startSession(command);
	}

	@Override
	public void addMessage(String sessionId, Message message) {
		sessionLifecycle.addMessage(sessionId, message);
	}

	public void addMessage(String userId, String sessionId, Message message) {
		sessionLifecycle.addMessage(userId, sessionId, message);
	}

	@Override
	public void endSession(String sessionId) {
		sessionLifecycle.endSession(sessionId);
	}

	public void endSession(String userId, String sessionId, String stopTime) {
		sessionLifecycle.endSession(userId, sessionId, stopTime);
	}

	@Override
	public SessionDetail getSession(String sessionId) {
		return sessionLifecycle.getSession(sessionId);
	}

	@Override
	public List<SessionDetail> getBySceneId(String sceneId) {
		return sessionLifecycle.getBySceneId(sceneId);
	}

	private void requireSceneType(StartSessionCommand command, SceneType expected) {
		if (command == null || command.sceneType() != expected) {
			throw new BusinessException(
					"SESSION_SCENE_TYPE_MISMATCH",
					"session command does not belong to " + expected);
		}
	}

}
