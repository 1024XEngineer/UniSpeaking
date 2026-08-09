package com.unispeaking.component.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.session.EndCustomSessionCommand;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.session.CustomSessionService;
import com.unispeaking.service.session.FreeChatSessionService;
import com.unispeaking.service.session.IeltsSessionService;
import com.unispeaking.service.session.InterviewSessionService;
import org.junit.jupiter.api.Test;

class SessionMessageDispatcherTest {

	@Test
	void dispatchesCustomMessagesAndEndUsingTheThreeMethodContract() {
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		FreeChatSessionService freeChat = mock(FreeChatSessionService.class);
		CustomSessionService custom = mock(CustomSessionService.class);
		IeltsSessionService ielts = mock(IeltsSessionService.class);
		InterviewSessionService interview = mock(InterviewSessionService.class);
		SessionMessageDispatcher dispatcher = new SessionMessageDispatcher(
				lifecycle,
				freeChat,
				custom,
				ielts,
				interview);
		String userId = "user-1";
		String sessionId = "session-1";
		String sceneId = "scene-1";
		Message message = new Message(1, "hello", null);
		when(lifecycle.requireSceneType(userId, sessionId))
				.thenReturn(SceneType.CUSTOM_SCENE);
		when(lifecycle.requireSceneId(sessionId, SceneType.CUSTOM_SCENE))
				.thenReturn(sceneId);

		dispatcher.addMessage(userId, sessionId, message);
		dispatcher.endSession(userId, sessionId, "client-time");

		verify(custom).addMessage(sessionId, message);
		verify(custom).endSession(new EndCustomSessionCommand(
				sceneId,
				sessionId,
				"client-time"));
	}

	@Test
	void dispatchesInterviewMessagesThroughTheStandardWsPath() {
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		FreeChatSessionService freeChat = mock(FreeChatSessionService.class);
		CustomSessionService custom = mock(CustomSessionService.class);
		IeltsSessionService ielts = mock(IeltsSessionService.class);
		InterviewSessionService interview = mock(InterviewSessionService.class);
		SessionMessageDispatcher dispatcher = new SessionMessageDispatcher(
				lifecycle,
				freeChat,
				custom,
				ielts,
				interview);
		Message message = new Message(0, "AI 问题字幕", null);
		when(lifecycle.requireSceneType("user-1", "session-1"))
				.thenReturn(SceneType.INTERVIEW_SCENE);

		dispatcher.addMessage("user-1", "session-1", message);

		verify(interview).addMessage("session-1", message);
	}

	@Test
	void dispatchesFreeChatEndWithoutExposingTransportArguments() {
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		FreeChatSessionService freeChat = mock(FreeChatSessionService.class);
		SessionMessageDispatcher dispatcher = new SessionMessageDispatcher(
				lifecycle,
				freeChat,
				mock(CustomSessionService.class),
				mock(IeltsSessionService.class),
				mock(InterviewSessionService.class));
		when(lifecycle.requireSceneType("user-1", "session-1"))
				.thenReturn(SceneType.FREE_CHAT);

		dispatcher.endSession("user-1", "session-1", "ignored-client-time");

		verify(freeChat).endSession("session-1");
	}
}
