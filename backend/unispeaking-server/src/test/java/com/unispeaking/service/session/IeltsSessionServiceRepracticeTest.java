package com.unispeaking.service.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.dto.scene.IeltsDialogueSceneContext;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartIeltsDialogueRequest;
import com.unispeaking.domain.dto.session.StartIeltsSessionCommand;
import com.unispeaking.domain.dto.session.StartIeltsSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionCommand;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import com.unispeaking.service.scene.IeltsSceneService;
import com.unispeaking.service.session.IeltsSessionService;
import org.junit.jupiter.api.Test;

class IeltsSessionServiceRepracticeTest {

	@Test
	void startSessionPreparesLifecycleStateAndConnectsRealtimeSession() {
		IeltsSceneService scenes = mock(IeltsSceneService.class);
		IeltsSceneFlowService flow = mock(IeltsSceneFlowService.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		IeltsSessionService service = ieltsService(scenes, flow, lifecycle, coordinator);
		IeltsContent content = new IeltsContent(null, null, null);
		IeltsDialogueSceneContext prepared = new IeltsDialogueSceneContext(
				"user-1",
				"ielts-1",
				content,
				IeltsPart.PART_2,
				"Describe a useful object",
				new SceneFlowResponse(
						"ielts-1",
						SceneFlowStage.IELTS_PART_2,
						false),
				"IELTS prompt",
				"Katerina");
		StartIeltsDialogueRequest request = new StartIeltsDialogueRequest(
				"offer-sdp",
				ProviderType.QWEN,
				"qwen3.5-plus",
				"Katerina",
				true);
		StartIeltsSessionCommand command = new StartIeltsSessionCommand(
				"ielts-1",
				request);
		StartSessionResponse started = new StartSessionResponse(
				"session-1",
				"2026-08-21T00:00:00Z");
		StartIeltsSessionResponse expected = mock(StartIeltsSessionResponse.class);
		when(scenes.prepareDialogue("ielts-1", "Katerina"))
				.thenReturn(prepared);
		when(lifecycle.startSession(any(StartSessionCommand.class)))
				.thenReturn(started);
		when(coordinator.connectIelts(
					eq(content),
					eq(IeltsPart.PART_2),
					eq("Describe a useful object"),
					eq(SceneFlowStage.IELTS_PART_2),
					eq(true),
					eq(started),
					eq("ielts-1"),
					eq("IELTS prompt"),
					eq("offer-sdp"),
					eq(ProviderType.QWEN),
					eq("qwen3.5-plus"),
					eq("Katerina"),
					eq(true)))
				.thenReturn(expected);

		StartIeltsSessionResponse actual = service.startSession(command);

		assertSame(expected, actual);
		var lifecycleCommand = org.mockito.ArgumentCaptor
				.forClass(StartSessionCommand.class);
		verify(lifecycle).startSession(lifecycleCommand.capture());
		assertEquals("user-1", lifecycleCommand.getValue().userId());
		assertEquals("ielts-1", lifecycleCommand.getValue().sceneId());
		assertEquals(SceneType.IELTS_SCENE,
				lifecycleCommand.getValue().sceneType());
		assertEquals("PART2", lifecycleCommand.getValue().stage());
		assertEquals("IELTS prompt", lifecycleCommand.getValue().prompt());
		verify(flow).startSessionState(
				"ielts-1",
				"session-1",
				IeltsPart.PART_2);
	}

	@Test
	void startSessionPropagatesScenePermissionFailureBeforePersistence() {
		IeltsSceneService scenes = mock(IeltsSceneService.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		IeltsSessionService service = ieltsService(
				scenes,
				mock(IeltsSceneFlowService.class),
				lifecycle,
				mock(RealtimeSessionCoordinator.class));
		BusinessException denied = new BusinessException(
				"IELTS_PRACTICE_ACCESS_DENIED",
				"当前用户无权访问该 IELTS 练习");
		when(scenes.prepareDialogue("ielts-forbidden", "Katerina"))
				.thenThrow(denied);

		BusinessException actual = assertThrows(
				BusinessException.class,
				() -> service.startSession(new StartIeltsSessionCommand(
						"ielts-forbidden",
						new StartIeltsDialogueRequest(
								"offer-sdp",
								ProviderType.QWEN,
								"qwen3.5-plus",
								"Katerina",
								false))));

		assertSame(denied, actual);
		verify(lifecycle, never()).startSession(any(StartSessionCommand.class));
	}

	@Test
	void startSessionPropagatesPersistenceFailureWithoutStartingRealtimeFlow() {
		IeltsSceneService scenes = mock(IeltsSceneService.class);
		IeltsSceneFlowService flow = mock(IeltsSceneFlowService.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		IeltsSessionService service = ieltsService(scenes, flow, lifecycle, coordinator);
		IeltsDialogueSceneContext prepared = preparedContext(IeltsPart.PART_1);
		RuntimeException persistenceFailure = new RuntimeException("practice insert failed");
		when(scenes.prepareDialogue("ielts-1", "Katerina")).thenReturn(prepared);
		when(lifecycle.startSession(any(StartSessionCommand.class)))
				.thenThrow(persistenceFailure);

		RuntimeException actual = assertThrows(
				RuntimeException.class,
				() -> service.startSession(command("ielts-1")));

		assertSame(persistenceFailure, actual);
		verifyNoInteractions(flow, coordinator);
	}

	@Test
	void startSessionClearsFlowStateWhenRealtimeConnectionFails() {
		IeltsSceneService scenes = mock(IeltsSceneService.class);
		IeltsSceneFlowService flow = mock(IeltsSceneFlowService.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		IeltsSessionService service = ieltsService(scenes, flow, lifecycle, coordinator);
		IeltsDialogueSceneContext prepared = preparedContext(IeltsPart.PART_3);
		StartSessionResponse started = new StartSessionResponse(
				"session-3",
				"2026-08-21T00:00:00Z");
		RuntimeException connectionFailure = new RuntimeException("SDP exchange failed");
		when(scenes.prepareDialogue("ielts-1", "Katerina")).thenReturn(prepared);
		when(lifecycle.startSession(any(StartSessionCommand.class))).thenReturn(started);
		when(coordinator.connectIelts(
				any(), any(), any(), any(), any(Boolean.class), any(), any(), any(), any(),
				any(), any(), any(), any(Boolean.class))).thenThrow(connectionFailure);

		RuntimeException actual = assertThrows(
				RuntimeException.class,
				() -> service.startSession(command("ielts-1")));

		assertSame(connectionFailure, actual);
		verify(flow).startSessionState("ielts-1", "session-3", IeltsPart.PART_3);
		verify(flow).clearSessionState("session-3");
	}

	@Test
	void addMessageDelegatesValidMessageToLifecycle() {
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		IeltsSessionService service = ieltsService(
				mock(IeltsSceneService.class),
				mock(IeltsSceneFlowService.class),
				lifecycle,
				mock(RealtimeSessionCoordinator.class));
		Message message = new Message(1, "answer", new byte[] {1, 2});

		service.addMessage("session-1", message);

		verify(lifecycle).addMessage("session-1", message);
	}

	@Test
	void addMessagePropagatesInvalidInputFailure() {
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		IeltsSessionService service = ieltsService(
				mock(IeltsSceneService.class),
				mock(IeltsSceneFlowService.class),
				lifecycle,
				mock(RealtimeSessionCoordinator.class));
		BusinessException invalid = new BusinessException(
				"MESSAGE_REQUIRED",
				"消息不能为空");
		doThrow(invalid).when(lifecycle).addMessage(
				eq("session-1"),
				org.mockito.ArgumentMatchers.isNull(Message.class));

		BusinessException actual = assertThrows(
				BusinessException.class,
				() -> service.addMessage("session-1", null));

		assertSame(invalid, actual);
		verify(lifecycle).addMessage("session-1", null);
	}

	@Test
	void addMessagePropagatesPermissionFailure() {
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		IeltsSessionService service = ieltsService(
				mock(IeltsSceneService.class),
				mock(IeltsSceneFlowService.class),
				lifecycle,
				mock(RealtimeSessionCoordinator.class));
		BusinessException denied = new BusinessException(
				"SESSION_ACCESS_DENIED",
				"当前用户无权访问该会话");
		doThrow(denied).when(lifecycle).addMessage(
				eq("foreign-session"),
				any(Message.class));

		BusinessException actual = assertThrows(
				BusinessException.class,
				() -> service.addMessage(
						"foreign-session",
						new Message(0, "not yours", null)));

		assertSame(denied, actual);
		verify(lifecycle).addMessage(
				eq("foreign-session"),
				any(Message.class));
	}

	@Test
	void addMessagePropagatesPersistenceFailure() {
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		IeltsSessionService service = ieltsService(
				mock(IeltsSceneService.class),
				mock(IeltsSceneFlowService.class),
				lifecycle,
				mock(RealtimeSessionCoordinator.class));
		RuntimeException persistenceFailure = new RuntimeException("message insert failed");
		Message message = new Message(1, "answer", null);
		doThrow(persistenceFailure).when(lifecycle).addMessage(
				eq("session-1"),
				eq(message));

		RuntimeException actual = assertThrows(
				RuntimeException.class,
				() -> service.addMessage("session-1", message));

		assertSame(persistenceFailure, actual);
	}

	@Test
	void sessionServiceOnlyCreatesTheGenericSessionLifecycle() {
		String userId = "f76889ee-7f7c-4dae-bcc2-61b85a63dcec";
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		PracticeSessionRepository practices = mock(PracticeSessionRepository.class);
		SessionLifecycleManager service = new SessionLifecycleManager(
				sessions,
				mock(SessionMessageRepository.class),
				practices);

		var started = service.startSession(
				userId,
				SceneType.CUSTOM_SCENE,
				"custom_repeat123",
				"session prompt");

		assertNotNull(started.sessionId());
		assertEquals(SceneType.CUSTOM_SCENE,
				sessions.findById(started.sessionId()).orElseThrow().getSceneType());
		verify(practices).create(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void completedIeltsFlowConsumesOneDailyPractice() {
		String userId = "f76889ee-7f7c-4dae-bcc2-61b85a63dcec";
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		IeltsSceneService scenes = mock(IeltsSceneService.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		CustomSceneSession session = ieltsSession("ielts_session_1", userId, "ielts_part_1");
		when(lifecycle.requireSceneType(userId, session.getId()))
				.thenReturn(SceneType.IELTS_SCENE);
		when(lifecycle.requireOwnerId(session.getId()))
				.thenReturn(userId);
		when(coordinator.requireOwnedSession(userId, session.getId()))
				.thenReturn(session);
		IeltsSessionService service = ieltsService(scenes, lifecycle, coordinator);

		service.endSession(session.getId());

		verify(lifecycle).endSession(session.getId());
		verify(scenes).completeDialogue("ielts_part_1", userId);
	}

	@Test
	void intermediateMockPartDoesNotConsumeDailyPractice() {
		String userId = "f76889ee-7f7c-4dae-bcc2-61b85a63dcec";
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		IeltsSceneService scenes = mock(IeltsSceneService.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		CustomSceneSession session = ieltsSession("ielts_session_2", userId, "ielts_mock_1");
		when(lifecycle.requireSceneType(userId, session.getId()))
				.thenReturn(SceneType.IELTS_SCENE);
		when(lifecycle.requireOwnerId(session.getId()))
				.thenReturn(userId);
		when(coordinator.requireOwnedSession(userId, session.getId()))
				.thenReturn(session);
		IeltsSessionService service = ieltsService(scenes, lifecycle, coordinator);

		service.endSession(session.getId());

		verify(lifecycle).endSession(session.getId());
		verify(scenes).completeDialogue("ielts_mock_1", userId);
	}

	@Test
	void endSessionRejectsNonIeltsSessionBeforeClaimingOwnership() {
		String userId = "user-1";
		String sessionId = "free-chat-session";
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		IeltsSceneFlowService flow = mock(IeltsSceneFlowService.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		IeltsSessionService service = ieltsService(
				mock(IeltsSceneService.class), flow, lifecycle, coordinator);
		when(lifecycle.requireOwnerId(sessionId)).thenReturn(userId);
		when(lifecycle.requireSceneType(userId, sessionId))
				.thenReturn(SceneType.FREE_CHAT);

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> service.endSession(sessionId));

		assertEquals("IELTS_SESSION_MISMATCH", exception.code());
		verify(coordinator, never()).requireOwnedSession(any(), any());
		verify(flow, never()).clearSessionState(any());
	}

	@Test
	void endSessionClearsFlowStateWhenEndingLifecycleFails() {
		String userId = "user-1";
		String sessionId = "ielts-session";
		IeltsSceneService scenes = mock(IeltsSceneService.class);
		IeltsSceneFlowService flow = mock(IeltsSceneFlowService.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		CustomSceneSession session = ieltsSession(sessionId, userId, "ielts-1");
		RuntimeException failure = new RuntimeException("close failed");
		when(lifecycle.requireOwnerId(sessionId)).thenReturn(userId);
		when(lifecycle.requireSceneType(userId, sessionId))
				.thenReturn(SceneType.IELTS_SCENE);
		when(coordinator.requireOwnedSession(userId, sessionId)).thenReturn(session);
		doThrow(failure).when(lifecycle).endSession(sessionId);
		IeltsSessionService service = ieltsService(scenes, flow, lifecycle, coordinator);

		RuntimeException actual = assertThrows(
				RuntimeException.class,
				() -> service.endSession(sessionId));

		assertSame(failure, actual);
		verify(flow).clearSessionState(sessionId);
		verify(scenes, never()).completeDialogue(any(), any());
	}

	@Test
	void endSessionClearsFlowStateWhenCompletingDialogueFails() {
		String userId = "user-1";
		String sessionId = "ielts-session";
		IeltsSceneService scenes = mock(IeltsSceneService.class);
		IeltsSessionService service;
		IeltsSceneFlowService flow = mock(IeltsSceneFlowService.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		CustomSceneSession session = ieltsSession(sessionId, userId, "ielts-1");
		RuntimeException failure = new RuntimeException("completion failed");
		when(lifecycle.requireOwnerId(sessionId)).thenReturn(userId);
		when(lifecycle.requireSceneType(userId, sessionId))
				.thenReturn(SceneType.IELTS_SCENE);
		when(coordinator.requireOwnedSession(userId, sessionId)).thenReturn(session);
		when(scenes.completeDialogue("ielts-1", userId)).thenThrow(failure);
		service = ieltsService(scenes, flow, lifecycle, coordinator);

		RuntimeException actual = assertThrows(
				RuntimeException.class,
				() -> service.endSession(sessionId));

		assertSame(failure, actual);
		verify(lifecycle).endSession(sessionId);
		verify(flow).clearSessionState(sessionId);
	}

	private IeltsSessionService ieltsService(
			IeltsSceneService scenes,
			IeltsSceneFlowService flow,
			SessionLifecycleManager lifecycle,
			RealtimeSessionCoordinator coordinator) {
		return new IeltsSessionService(
				scenes,
				flow,
				lifecycle,
				coordinator);
	}

	private IeltsSessionService ieltsService(
			IeltsSceneService scenes,
			SessionLifecycleManager lifecycle,
			RealtimeSessionCoordinator coordinator) {
		return ieltsService(
				scenes,
				mock(IeltsSceneFlowService.class),
				lifecycle,
				coordinator);
	}

	private IeltsDialogueSceneContext preparedContext(IeltsPart part) {
		return new IeltsDialogueSceneContext(
				"user-1",
				"ielts-1",
				new IeltsContent(null, null, null),
				part,
				"IELTS topic",
				new SceneFlowResponse(
						"ielts-1",
						SceneFlowStage.valueOf("IELTS_" + part.name()),
						false),
				"IELTS prompt",
				"Katerina");
	}

	private StartIeltsSessionCommand command(String ieltsId) {
		return new StartIeltsSessionCommand(
				ieltsId,
				new StartIeltsDialogueRequest(
						"offer-sdp",
						ProviderType.QWEN,
						"qwen3.5-plus",
						"Katerina",
						true));
	}

	private CustomSceneSession ieltsSession(
			String sessionId,
			String userId,
			String sceneId) {
		CustomSceneSession session = new CustomSceneSession(sessionId, userId);
		session.setSceneType(SceneType.IELTS_SCENE);
		session.setSceneId(sceneId);
		return session;
	}
}
