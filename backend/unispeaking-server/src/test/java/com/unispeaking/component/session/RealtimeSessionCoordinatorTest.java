package com.unispeaking.component.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.realtime.RealtimeSdpExchange;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealtimeSessionCoordinatorTest {

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
						"answer-sdp",
						Instant.parse("2026-08-05T08:00:00Z")));
		RealtimeSessionCoordinator coordinator = new RealtimeSessionCoordinator(
				sessions,
				mock(PracticeSessionRepository.class),
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
	}
}
