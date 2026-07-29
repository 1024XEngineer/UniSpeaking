package com.unispeaking.orchestration;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.exception.SessionNotFoundException;
import com.unispeaking.repository.SessionStateStore;
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
	private final SessionStateStore sessionStateStore;
	private final Map<String, SessionService> sessions = new ConcurrentHashMap<>();

	public SessionServiceSelector(
			ObjectProvider<FreeChatSessionService> freeChatServices,
			ObjectProvider<CustomSceneSessionService> customSceneServices,
			SessionStateStore sessionStateStore) {
		this.freeChatServices = freeChatServices;
		this.customSceneServices = customSceneServices;
		this.sessionStateStore = sessionStateStore;
	}

	public StartSessionResponse startSession(SceneType sceneType, String prompt) {
		SessionService service = create(sceneType);
		StartSessionResponse response = service.startSession(prompt);
		sessions.put(response.sessionId(), service);
		return response;
	}

	public void addMessage(String userId, String sessionId, Message message) {
		resolveOwned(userId, sessionId).addMessage(message);
	}

	public void endSession(String userId, String sessionId, String stopTime) {
		SessionService service = resolveOwned(userId, sessionId);
		service.endSession(sessionId, stopTime);
		sessions.remove(sessionId, service);
	}

	private SessionService resolveOwned(String userId, String sessionId) {
		if (userId == null || userId.isBlank()) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "请先登录");
		}
		if (sessionId == null || sessionId.isBlank()) {
			throw new SessionNotFoundException(String.valueOf(sessionId));
		}
		var session = sessionStateStore.findById(sessionId)
				.orElseThrow(() -> new SessionNotFoundException(sessionId));
		if (!userId.equals(session.getUserId())) {
			throw new BusinessException("SESSION_ACCESS_DENIED", "当前用户无权访问该会话");
		}
		return resolve(sessionId);
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
