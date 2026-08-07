package com.unispeaking.component.session;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.EndCustomSessionCommand;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.session.CustomSessionService;
import com.unispeaking.service.session.FreeChatSessionService;
import com.unispeaking.service.session.IeltsSessionService;
import org.springframework.stereotype.Component;

/** Routes transport-level session events to the owning scene implementation. */
@Component
public class SessionMessageDispatcher {

	private final SessionLifecycleManager lifecycle;
	private final FreeChatSessionService freeChatSessions;
	private final CustomSessionService customSessions;
	private final IeltsSessionService ieltsSessions;

	public SessionMessageDispatcher(
			SessionLifecycleManager lifecycle,
			FreeChatSessionService freeChatSessions,
			CustomSessionService customSessions,
			IeltsSessionService ieltsSessions) {
		this.lifecycle = lifecycle;
		this.freeChatSessions = freeChatSessions;
		this.customSessions = customSessions;
		this.ieltsSessions = ieltsSessions;
	}

	public void addMessage(String userId, String sessionId, Message message) {
			switch (sceneType(userId, sessionId)) {
			case FREE_CHAT -> freeChatSessions.addMessage(sessionId, message);
			case CUSTOM_SCENE -> customSessions.addMessage(sessionId, message);
			case IELTS_SCENE -> ieltsSessions.addMessage(sessionId, message);
		}
	}

	public void endSession(String userId, String sessionId, String stopTime) {
			switch (sceneType(userId, sessionId)) {
			case FREE_CHAT -> freeChatSessions.endSession(sessionId);
			case CUSTOM_SCENE -> customSessions.endSession(
					new EndCustomSessionCommand(
							lifecycle.requireSceneId(
									sessionId,
									SceneType.CUSTOM_SCENE),
							sessionId,
							stopTime));
			case IELTS_SCENE -> ieltsSessions.endSession(sessionId);
		}
	}

	private SceneType sceneType(String userId, String sessionId) {
		return lifecycle.requireSceneType(userId, sessionId);
	}
}
