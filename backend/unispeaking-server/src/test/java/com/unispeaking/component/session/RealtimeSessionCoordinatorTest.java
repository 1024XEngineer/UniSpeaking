package com.unispeaking.component.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.SessionNotFoundException;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.realtime.RealtimeSdpExchange;
import com.unispeaking.infrastructure.realtime.RealtimeSessionTerminator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealtimeSessionCoordinatorTest {

	private static final String USER_ID = "3d8f80be-6390-4db9-a6cf-c10a0145d4c3";

	@Test
	void bindsIeltsPartToSessionBeforeRealtimeConnection() {
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		CustomSceneSession session = new CustomSceneSession(
				"session-part-2",
				"3d8f80be-6390-4db9-a6cf-c10a0145d4c3");
		sessions.save(session);
		RealtimeSdpExchange exchange = mock(RealtimeSdpExchange.class);
		when(exchange.exchangeSdp(any(), any(), any(), any())).thenReturn(
				new RealtimeConnectionResult(
						"provider-session",
						ProviderType.QINIU,
						"qwen3.5-omni-plus-realtime",
						"Tina",
						"trace-1",
						"answer-sdp",
						Instant.parse("2026-08-05T08:00:00Z")));
		PracticeSessionRepository practices = mock(PracticeSessionRepository.class);
		RealtimeSessionCoordinator coordinator = new RealtimeSessionCoordinator(
				sessions,
				practices,
				exchange);

		coordinator.connectIelts(
				new IeltsContent(List.of(), List.of(), List.of()),
				IeltsPart.PART_2,
				"IELTS Part 2",
				SceneFlowStage.IELTS_PART_2,
				true,
				new StartSessionResponse("session-part-2", "2026-08-05T08:00:00Z"),
				"ielts_mock_1",
				"prompt",
				"offer-sdp",
				ProviderType.QWEN,
				null,
				"Margaret",
				false);

		assertEquals(
				IeltsPart.PART_2,
				sessions.findById("session-part-2").orElseThrow().getIeltsPart());
		assertEquals(
				ProviderType.QINIU,
				sessions.findById("session-part-2").orElseThrow().getProviderType());
		assertEquals(
				"Tina",
				sessions.findById("session-part-2").orElseThrow().getVoiceId());
		verify(practices).updateRealtimeProvider(
				"session-part-2",
				java.util.UUID.fromString("3d8f80be-6390-4db9-a6cf-c10a0145d4c3"),
				"provider-session",
				ProviderType.QINIU,
				"qwen3.5-omni-plus-realtime",
				"trace-1");
	}

	@Test
	void requiresAnExistingSessionOwnedByAnAuthenticatedUser() {
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		RealtimeSessionCoordinator coordinator = new RealtimeSessionCoordinator(
			sessions, mock(PracticeSessionRepository.class), mock(RealtimeSdpExchange.class));

		assertEquals("AUTHENTICATION_REQUIRED", assertThrows(BusinessException.class,
				() -> coordinator.requireOwnedSession(" ", "missing")).code());
		assertThrows(SessionNotFoundException.class,
				() -> coordinator.requireOwnedSession(USER_ID, "missing"));

		CustomSceneSession session = new CustomSceneSession("owned", USER_ID);
		sessions.save(session);
		assertSame(session, coordinator.requireOwnedSession(USER_ID, "owned"));
		assertEquals("SESSION_ACCESS_DENIED", assertThrows(BusinessException.class,
				() -> coordinator.requireOwnedSession(UUID.randomUUID().toString(), "owned")).code());
	}

	@Test
	void connectsWithDefaultVoiceAndDoesNotBindBlankProviderSession() {
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		CustomSceneSession session = new CustomSceneSession("connect-default", USER_ID);
		sessions.save(session);
		RealtimeSdpExchange exchange = mock(RealtimeSdpExchange.class);
		PracticeSessionRepository practices = mock(PracticeSessionRepository.class);
		when(exchange.exchangeSdp(any(), any(), any(), any())).thenReturn(
				new RealtimeConnectionResult(null, ProviderType.QWEN, "model-1", " ",
						"trace-1", "answer", null));
		RealtimeSessionCoordinator coordinator = new RealtimeSessionCoordinator(
				sessions, practices, exchange);

		var result = coordinator.connect(
				new SceneGenerationResponse("scene-1", List.of(), List.of(), List.of(), "prompt"),
				"Scene", SceneFlowStage.DIALOGUE, false,
				new StartSessionResponse(session.getId(), "2026-08-05T08:00:00Z"),
				SceneType.CUSTOM_SCENE, "scene-1", "prompt", "offer", ProviderType.QWEN,
				null, "  ", false);

		assertEquals("Tina", result.voiceId());
		assertEquals("answer", result.answerSdp());
		assertEquals(null, result.providerSessionId());
		assertEquals("Tina", session.getVoiceId());
		assertEquals(com.unispeaking.domain.vo.session.SessionStatus.WAITING_CLIENT, session.getStatus());
		verify(practices).updateRealtimeProvider(
				eq(session.getId()), eq(UUID.fromString(USER_ID)), eq((String) null),
				eq(ProviderType.QWEN), eq("model-1"), eq("trace-1"));
	}

	@Test
	void retainsRequestedVoiceWhenProviderReturnsNoVoiceAndRemovesSessionAfterFailure() {
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		CustomSceneSession session = new CustomSceneSession("connect-failed", USER_ID);
		sessions.save(session);
		RealtimeSdpExchange exchange = mock(RealtimeSdpExchange.class);
		PracticeSessionRepository practices = mock(PracticeSessionRepository.class);
		RealtimeSessionTerminator terminator = mock(RealtimeSessionTerminator.class);
		RuntimeException failure = new IllegalStateException("exchange failed");
		when(exchange.exchangeSdp(any(), any(), any(), any())).thenThrow(failure);
		RealtimeSessionCoordinator coordinator = new RealtimeSessionCoordinator(
				sessions, practices, exchange, terminator);

		assertSame(failure, assertThrows(RuntimeException.class, () -> coordinator.connect(
				new SceneGenerationResponse("scene-1", List.of(), List.of(), List.of(), "prompt"),
				"Scene", SceneFlowStage.DIALOGUE, true,
				new StartSessionResponse(session.getId(), "2026-08-05T08:00:00Z"),
				SceneType.CUSTOM_SCENE, "scene-1", "prompt", "offer", ProviderType.QWEN,
				"model", "Margaret", true)));

		verify(terminator).stopBestEffort(session, "local_start_failed");
		verify(practices).fail(eq(session.getId()), eq(UUID.fromString(USER_ID)), any());
		assertEquals(com.unispeaking.domain.vo.session.SessionStatus.FAILED, session.getStatus());
		assertEquals("Margaret", session.getVoiceId());
		assertTrue(sessions.findById(session.getId()).isEmpty());
	}

	@Test
	void removesAnActiveSessionExplicitlyAndReportsMissingIeltsSession() {
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		CustomSceneSession session = new CustomSceneSession("to-remove", USER_ID);
		sessions.save(session);
		RealtimeSessionCoordinator coordinator = new RealtimeSessionCoordinator(
				sessions, mock(PracticeSessionRepository.class), mock(RealtimeSdpExchange.class));

		coordinator.remove(session.getId());
		assertTrue(sessions.findById(session.getId()).isEmpty());
		assertThrows(SessionNotFoundException.class, () -> coordinator.connectIelts(
				new IeltsContent(List.of(), List.of(), List.of()), IeltsPart.PART_1,
				"IELTS", SceneFlowStage.IELTS_PART_1, true,
				new StartSessionResponse("missing", "2026-08-05T08:00:00Z"), "scene", "prompt",
				"offer", ProviderType.QWEN, "model", "Tina", false));
	}
}
