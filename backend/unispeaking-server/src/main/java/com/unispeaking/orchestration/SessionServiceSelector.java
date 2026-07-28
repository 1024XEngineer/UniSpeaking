package com.unispeaking.orchestration;

import com.unispeaking.domain.dto.session.AddSessionMessageRequest;
import com.unispeaking.domain.dto.session.EndSessionRequest;
import com.unispeaking.domain.dto.session.EndSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionRequest;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.exception.SessionNotFoundException;
import com.unispeaking.repository.SessionStateStore;
import com.unispeaking.service.session.CustomSceneSessionService;
import com.unispeaking.service.session.FreeChatSessionService;
import com.unispeaking.service.session.SessionService;
import org.springframework.stereotype.Component;

@Component
public class SessionServiceSelector {

	private final SessionStateStore sessionStateStore;
	private final FreeChatSessionService freeChatSessionService;
	private final CustomSceneSessionService customSceneSessionService;

	public SessionServiceSelector(
			SessionStateStore sessionStateStore,
			FreeChatSessionService freeChatSessionService,
			CustomSceneSessionService customSceneSessionService) {
		this.sessionStateStore = sessionStateStore;
		this.freeChatSessionService = freeChatSessionService;
		this.customSceneSessionService = customSceneSessionService;
	}

	public StartSessionResponse startSession(StartSessionRequest request) {
		return resolve(request.sceneType()).startSession(request);
	}

	public void addMessage(AddSessionMessageRequest request) {
		resolveBySessionId(request.sessionId()).addMessage(request);
	}

	public EndSessionResponse endSession(EndSessionRequest request) {
		return resolveBySessionId(request.sessionId()).endSession(request);
	}

	private SessionService resolveBySessionId(String sessionId) {
		AbstractSceneSession session = sessionStateStore.findById(sessionId)
				.orElseThrow(() -> new SessionNotFoundException(sessionId));
		return resolve(session.getSceneType());
	}

	private SessionService resolve(SceneType sceneType) {
		if (sceneType == SceneType.FREE_CHAT || sceneType == null) {
			return freeChatSessionService;
		}
		return customSceneSessionService;
	}
}
