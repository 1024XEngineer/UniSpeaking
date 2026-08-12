package com.unispeaking.component.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.unispeaking.domain.po.session.FreeChatSceneSession;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.infrastructure.realtime.RealtimeSessionTerminator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionLifecycleRealtimeStopTest {

	@Test
	void stopsTheExternalRealtimeSessionAfterPersistingTheTerminalState() {
		UUID ownerId = UUID.randomUUID();
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		FreeChatSceneSession session = new FreeChatSceneSession("local-1", ownerId.toString());
		session.setSceneType(SceneType.FREE_CHAT);
		session.setProviderType(ProviderType.QINIU);
		session.setModel("qwen3.5-omni-plus-realtime");
		session.bindProviderSession("rti-session-1");
		sessions.save(session);
		PracticeSessionRepository practices = mock(PracticeSessionRepository.class);
		RealtimeSessionTerminator terminator = mock(RealtimeSessionTerminator.class);
		SessionLifecycleManager lifecycle = new SessionLifecycleManager(
				sessions,
				mock(SessionMessageRepository.class),
				practices,
				null,
				terminator);
		Instant endedAt = Instant.parse("2026-08-12T05:00:00Z");

		lifecycle.terminateSceneSession(
				ownerId.toString(),
				session.getId(),
				SessionStatus.COMPLETED,
				endedAt);

		verify(practices).complete(session.getId(), ownerId, endedAt);
		verify(terminator).stopBestEffort(session, "client_completed");
	}
}
