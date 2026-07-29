package com.unispeaking.orchestration;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.exception.SessionNotFoundException;
import com.unispeaking.service.session.CustomSceneSessionService;
import com.unispeaking.service.session.FreeChatSessionService;
import com.unispeaking.service.session.SessionService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class SessionServiceSelector {

	private final ObjectProvider<FreeChatSessionService> freeChatServices;
	private final ObjectProvider<CustomSceneSessionService> customSceneServices;
	private final Map<String, SessionService> sessions = new ConcurrentHashMap<>();

	public SessionServiceSelector(
			ObjectProvider<FreeChatSessionService> freeChatServices,
			ObjectProvider<CustomSceneSessionService> customSceneServices) {
		this.freeChatServices = freeChatServices;
		this.customSceneServices = customSceneServices;
	}

	public StartSessionResponse startSession(SceneType sceneType, String prompt) {
		SessionService service = create(sceneType);
		StartSessionResponse response = service.startSession(prompt);
		sessions.put(response.sessionId(), service);
		return response;
	}

	public void addMessage(String sessionId, Message message) {
		resolve(sessionId).addMessage(message);
	}

	public void endSession(String sessionId, String stopTime) {
		SessionService service = resolve(sessionId);
		service.endSession(sessionId, stopTime);
		sessions.remove(sessionId, service);
	}

	private SessionService resolve(String sessionId) {
		SessionService service = sessions.get(sessionId);
		if (service == null) {
			throw new SessionNotFoundException(sessionId);
		}
		return service;
	}

	private SessionService create(SceneType sceneType) {
		if (sceneType == SceneType.FREE_CHAT || sceneType == null) {
			return freeChatServices.getObject();
		}
		return customSceneServices.getObject();
	}
}
