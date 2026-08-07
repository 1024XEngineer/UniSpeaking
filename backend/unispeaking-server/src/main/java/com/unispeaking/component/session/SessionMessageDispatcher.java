package com.unispeaking.component.session;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.session.impl.CustomSessionServiceImpl;
import com.unispeaking.service.session.impl.FreeChatSessionServiceImpl;
import com.unispeaking.service.session.impl.IeltsSessionServiceImpl;
import org.springframework.stereotype.Component;

/** Routes transport-level session events to the owning scene implementation. */
@Component
public class SessionMessageDispatcher {

	private final SessionLifecycleManager lifecycle;
	private final FreeChatSessionServiceImpl freeChatSessions;
	private final CustomSessionServiceImpl customSessions;
	private final IeltsSessionServiceImpl ieltsSessions;

	public SessionMessageDispatcher(
			SessionLifecycleManager lifecycle,
			FreeChatSessionServiceImpl freeChatSessions,
			CustomSessionServiceImpl customSessions,
			IeltsSessionServiceImpl ieltsSessions) {
		this.lifecycle = lifecycle;
		this.freeChatSessions = freeChatSessions;
		this.customSessions = customSessions;
		this.ieltsSessions = ieltsSessions;
	}

	public void addMessage(String userId, String sessionId, Message message) {
			switch (sceneType(userId, sessionId)) {
			case FREE_CHAT -> freeChatSessions.addMessage(userId, sessionId, message);
			case CUSTOM_SCENE -> customSessions.addMessage(userId, sessionId, message);
			case IELTS_SCENE -> ieltsSessions.addMessage(userId, sessionId, message);
		}
	}

	public void endSession(String userId, String sessionId, String stopTime) {
			switch (sceneType(userId, sessionId)) {
			case FREE_CHAT -> freeChatSessions.endSession(userId, sessionId, stopTime);
			case CUSTOM_SCENE -> customSessions.endSession(userId, sessionId, stopTime);
			case IELTS_SCENE -> ieltsSessions.endSession(userId, sessionId, stopTime);
		}
	}

	private SceneType sceneType(String userId, String sessionId) {
		return lifecycle.requireSceneType(userId, sessionId);
	}
}
