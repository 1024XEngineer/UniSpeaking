package com.unispeaking.service.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.component.session.RealtimeSessionCoordinator;
import com.unispeaking.component.session.SessionLifecycleManager;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.service.scene.IeltsSceneFlowService;
import com.unispeaking.service.scene.impl.IeltsSceneServiceImpl;
import com.unispeaking.service.session.impl.IeltsSessionServiceImpl;
import org.junit.jupiter.api.Test;

class SessionServiceImplRepracticeTest {

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
		IeltsSceneServiceImpl scenes = mock(IeltsSceneServiceImpl.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		CustomSceneSession session = ieltsSession("ielts_session_1", userId, "ielts_part_1");
		when(lifecycle.requireSceneType(userId, session.getId()))
				.thenReturn(SceneType.IELTS_SCENE);
		when(lifecycle.requireOwnerId(session.getId()))
				.thenReturn(userId);
		when(coordinator.requireOwnedSession(userId, session.getId()))
				.thenReturn(session);
		IeltsSessionServiceImpl service = ieltsService(scenes, lifecycle, coordinator);

		service.endSession(session.getId());

		verify(lifecycle).endSession(session.getId());
		verify(scenes).completeDialogue("ielts_part_1", userId);
	}

	@Test
	void intermediateMockPartDoesNotConsumeDailyPractice() {
		String userId = "f76889ee-7f7c-4dae-bcc2-61b85a63dcec";
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		IeltsSceneServiceImpl scenes = mock(IeltsSceneServiceImpl.class);
		SessionLifecycleManager lifecycle = mock(SessionLifecycleManager.class);
		RealtimeSessionCoordinator coordinator = mock(RealtimeSessionCoordinator.class);
		CustomSceneSession session = ieltsSession("ielts_session_2", userId, "ielts_mock_1");
		when(lifecycle.requireSceneType(userId, session.getId()))
				.thenReturn(SceneType.IELTS_SCENE);
		when(lifecycle.requireOwnerId(session.getId()))
				.thenReturn(userId);
		when(coordinator.requireOwnedSession(userId, session.getId()))
				.thenReturn(session);
		IeltsSessionServiceImpl service = ieltsService(scenes, lifecycle, coordinator);

		service.endSession(session.getId());

		verify(lifecycle).endSession(session.getId());
		verify(scenes).completeDialogue("ielts_mock_1", userId);
	}

	private IeltsSessionServiceImpl ieltsService(
			IeltsSceneServiceImpl scenes,
			SessionLifecycleManager lifecycle,
			RealtimeSessionCoordinator coordinator) {
		return new IeltsSessionServiceImpl(
				scenes,
				mock(IeltsSceneFlowService.class),
				lifecycle,
				coordinator);
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
