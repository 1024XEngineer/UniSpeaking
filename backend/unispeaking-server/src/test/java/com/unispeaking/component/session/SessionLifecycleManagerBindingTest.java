package com.unispeaking.component.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.session.FreeChatSceneSession;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionLifecycleManagerBindingTest {
	@Test
	void persistsProviderSessionBindingForTheOwnedActiveSession() {
		UUID ownerId = UUID.randomUUID();
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		FreeChatSceneSession session = new FreeChatSceneSession("local-1", ownerId.toString());
		sessions.save(session);
		PracticeSessionRepository practices = mock(PracticeSessionRepository.class);
		SessionLifecycleManager lifecycle = new SessionLifecycleManager(
				sessions, mock(SessionMessageRepository.class), practices);

		lifecycle.bindProviderSession(ownerId.toString(), "local-1", "sess_qwen_1");

		verify(practices).bindProviderSession("local-1", ownerId, "sess_qwen_1");
		assertEquals("sess_qwen_1", session.getProviderSessionId());
	}

	@Test
	void rejectsCrossUserAndConflictingProviderSessionBindings() {
		UUID ownerId = UUID.randomUUID();
		ActiveSessionRegistry sessions = new ActiveSessionRegistry();
		FreeChatSceneSession session = new FreeChatSceneSession("local-1", ownerId.toString());
		sessions.save(session);
		PracticeSessionRepository practices = mock(PracticeSessionRepository.class);
		SessionLifecycleManager lifecycle = new SessionLifecycleManager(
				sessions, mock(SessionMessageRepository.class), practices);

		BusinessException crossUser = assertThrows(BusinessException.class,
				() -> lifecycle.bindProviderSession(UUID.randomUUID().toString(), "local-1", "sess_stolen"));
		assertEquals("SESSION_ACCESS_DENIED", crossUser.code());
		verifyNoInteractions(practices);

		lifecycle.bindProviderSession(ownerId.toString(), "local-1", "sess_qwen_1");
		BusinessException conflicting = assertThrows(BusinessException.class,
				() -> lifecycle.bindProviderSession(ownerId.toString(), "local-1", "sess_qwen_2"));
		assertEquals("PROVIDER_SESSION_CONFLICT", conflicting.code());
	}
}
