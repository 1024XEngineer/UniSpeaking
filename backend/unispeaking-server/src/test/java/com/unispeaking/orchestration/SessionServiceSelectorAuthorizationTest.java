package com.unispeaking.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.component.SessionIdGenerator;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import com.unispeaking.domain.dto.auth.UserAccountResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.conversation.ConversationMessage;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.repository.SessionStateStore;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.conversation.FreeChatConversationService;
import com.unispeaking.service.quota.UsageQuotaService;
import com.unispeaking.service.session.CustomSceneSessionService;
import com.unispeaking.service.session.FreeChatSessionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class SessionServiceSelectorAuthorizationTest {

	@Test
	void rejectsMessageAndEndOperationsFromNonOwner() {
		var stateStore = new TestSessionStateStore();
		var conversations = new RecordingConversationService();
		var sessionService = new FreeChatSessionService(
				stateStore,
				new FixedAuthService(),
				new NoOpUsageQuotaService(),
				new FixedSessionIdGenerator(),
				conversations);
		var freeChatFactory = new StaticListableBeanFactory();
		freeChatFactory.addBean("freeChatSessionService", sessionService);
		var emptyFactory = new StaticListableBeanFactory();
		var selector = new SessionServiceSelector(
				freeChatFactory.getBeanProvider(FreeChatSessionService.class),
				emptyFactory.getBeanProvider(CustomSceneSessionService.class),
				stateStore);
		String sessionId = selector.startSession(SceneType.FREE_CHAT, "prompt").sessionId();

		BusinessException addError = assertThrows(
				BusinessException.class,
				() -> selector.addMessage("attacker", sessionId, new Message(1, "tampered", null)));
		BusinessException endError = assertThrows(
				BusinessException.class,
				() -> selector.endSession("attacker", sessionId, null));

		assertEquals("SESSION_ACCESS_DENIED", addError.code());
		assertEquals("SESSION_ACCESS_DENIED", endError.code());
		assertEquals(0, conversations.messages.size());

		selector.addMessage("owner-user", sessionId, new Message(1, "allowed", null));
		assertEquals("allowed", conversations.messages.getFirst().text());
	}

	private static final class FixedAuthService implements AuthService {
		@Override
		public AuthResponse register(RegisterRequest request) {
			throw new UnsupportedOperationException();
		}

		@Override
		public AuthResponse login(LoginRequest request) {
			throw new UnsupportedOperationException();
		}

		@Override
		public UserAccountResponse currentUser() {
			throw new UnsupportedOperationException();
		}

		@Override
		public String requireUserId(String requestedUserId) {
			return "owner-user";
		}
	}

	private static final class FixedSessionIdGenerator extends SessionIdGenerator {
		@Override
		public String generate() {
			return "freechat_authorized";
		}
	}

	private static final class TestSessionStateStore implements SessionStateStore {
		private final Map<String, AbstractSceneSession> sessions = new ConcurrentHashMap<>();

		@Override
		public void save(AbstractSceneSession session) {
			sessions.put(session.getId(), session);
		}

		@Override
		public Optional<AbstractSceneSession> findById(String localSessionId) {
			return Optional.ofNullable(sessions.get(localSessionId));
		}

		@Override
		public void remove(String localSessionId) {
			sessions.remove(localSessionId);
		}
	}

	private static final class RecordingConversationService implements FreeChatConversationService {
		private final List<ConversationMessage> messages = new ArrayList<>();

		@Override
		public void appendMessage(ConversationMessage message) {
			messages.add(message);
		}

		@Override
		public List<ConversationMessage> getMessages(String localSessionId) {
			return List.copyOf(messages);
		}

		@Override
		public void clearConversation(String localSessionId) {
			messages.clear();
		}
	}

	private static final class NoOpUsageQuotaService implements UsageQuotaService {
		@Override
		public void reserve(String userId, String localSessionId) {
		}

		@Override
		public void startMetering(String localSessionId) {
		}

		@Override
		public void settle(String localSessionId) {
		}

		@Override
		public void settleReservedQuota(String localSessionId) {
		}
	}
}
