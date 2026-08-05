package com.unispeaking.service.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.unispeaking.component.session.ActiveSessionRegistry;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.dto.scene.SceneFlowResponse;
import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.scene.SceneFlowService;
import com.unispeaking.service.session.impl.SessionServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class SessionServiceImplRepracticeTest {

	@Test
	void sessionServiceOnlyCreatesTheGenericSessionLifecycle() {
		String userId = "f76889ee-7f7c-4dae-bcc2-61b85a63dcec";
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(isNull())).thenReturn(userId);
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		PracticeSessionRepository practices = mock(PracticeSessionRepository.class);
		SessionServiceImpl service = new SessionServiceImpl(
				authService,
				sessions,
				mock(SessionMessageRepository.class),
				practices,
				mock(IeltsPracticeRepository.class),
				mock(SceneFlowService.class));

		var started = service.startSession(
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
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(isNull())).thenReturn(userId);
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		IeltsPracticeRepository ieltsPractices = mock(IeltsPracticeRepository.class);
		SceneFlowService flows = mock(SceneFlowService.class);
		when(flows.advanceStage("ielts_part_1", null)).thenReturn(
				new SceneFlowResponse(
						"ielts_part_1",
						SceneFlowStage.COMPLETED,
						true));
		SessionServiceImpl service = new SessionServiceImpl(
				authService,
				sessions,
				mock(SessionMessageRepository.class),
				mock(PracticeSessionRepository.class),
				ieltsPractices,
				flows);
		var started = service.startSession(
				SceneType.IELTS_SCENE,
				"ielts_part_1",
				"IELTS prompt");

		service.endSession(userId, started.sessionId(), null);

		verify(ieltsPractices).incrementCompletedCount(UUID.fromString(userId));
		verify(flows).completeFlow("ielts_part_1", true);
	}

	@Test
	void intermediateMockPartDoesNotConsumeDailyPractice() {
		String userId = "f76889ee-7f7c-4dae-bcc2-61b85a63dcec";
		AuthService authService = mock(AuthService.class);
		when(authService.requireUserId(isNull())).thenReturn(userId);
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		IeltsPracticeRepository ieltsPractices = mock(IeltsPracticeRepository.class);
		SceneFlowService flows = mock(SceneFlowService.class);
		when(flows.advanceStage("ielts_mock_1", null)).thenReturn(
				new SceneFlowResponse(
						"ielts_mock_1",
						SceneFlowStage.IELTS_PART_2,
						false));
		SessionServiceImpl service = new SessionServiceImpl(
				authService,
				sessions,
				mock(SessionMessageRepository.class),
				mock(PracticeSessionRepository.class),
				ieltsPractices,
				flows);
		var started = service.startSession(
				SceneType.IELTS_SCENE,
				"ielts_mock_1",
				"IELTS prompt");

		service.endSession(userId, started.sessionId(), null);

		verify(ieltsPractices, never()).incrementCompletedCount(UUID.fromString(userId));
		verify(flows, never()).completeFlow("ielts_mock_1", true);
	}
}
