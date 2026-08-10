package com.unispeaking.component.session;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.EndCustomSessionCommand;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.session.CustomSessionService;
import com.unispeaking.service.session.FreeChatSessionService;
import com.unispeaking.service.session.IeltsSessionService;
import com.unispeaking.service.session.InterviewSessionService;
import org.springframework.stereotype.Component;

/** Routes transport-level session events to the owning scene implementation. */
@Component
public class SessionMessageDispatcher {

	private final SessionLifecycleManager lifecycle;
	private final FreeChatSessionService freeChatSessions;
	private final CustomSessionService customSessions;
	private final IeltsSessionService ieltsSessions;
	private final InterviewSessionService interviewSessions;

	public SessionMessageDispatcher(
			SessionLifecycleManager lifecycle,
			FreeChatSessionService freeChatSessions,
			CustomSessionService customSessions,
			IeltsSessionService ieltsSessions,
			InterviewSessionService interviewSessions) {
		this.lifecycle = lifecycle;
		this.freeChatSessions = freeChatSessions;
		this.customSessions = customSessions;
		this.ieltsSessions = ieltsSessions;
		this.interviewSessions = interviewSessions;
	}

	public void addMessage(String userId, String sessionId, Message message) {
			switch (sceneType(userId, sessionId)) {
			case FREE_CHAT -> freeChatSessions.addMessage(sessionId, message);
			case CUSTOM_SCENE -> customSessions.addMessage(sessionId, message);
			case IELTS_SCENE -> ieltsSessions.addMessage(sessionId, message);
			case INTERVIEW_SCENE -> interviewSessions.addMessage(sessionId, message);
		}
	}

	public void endSession(String userId, String sessionId, String stopTime) {
			// INTERVIEW_SCENE endSession 在本刀未实现（第四刀 submitTurn/第五刀 end 编排补齐），
			// 语句式 switch 非穷尽，此处保留空分支即编译通过；Interview 会话 WS end 暂不投递。
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
			case INTERVIEW_SCENE -> {
				// 本刀不投递 Interview endSession；后续由 endInterview 编排。
			}
		}
	}

	private SceneType sceneType(String userId, String sessionId) {
		return lifecycle.requireSceneType(userId, sessionId);
	}
}
