package com.unispeaking.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.unispeaking.component.SessionIdGenerator;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import com.unispeaking.domain.dto.auth.UserAccountResponse;
import com.unispeaking.domain.dto.command.StartCommand;
import com.unispeaking.domain.dto.scene.StartSceneSessionRequest;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.conversation.ConversationMessage;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.prompt.SessionPrompt;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.realtime.RealtimeConnectionResult;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.orchestration.RealtimeSessionConnector;
import com.unispeaking.repository.SessionStateStore;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.conversation.FreeChatConversationService;
import com.unispeaking.service.quota.UsageQuotaService;
import com.unispeaking.service.realtime.RealtimeConnectionService;
import com.unispeaking.service.session.FreeChatSessionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class SessionRealtimeConnectionTest {

	@Test
	void sessionServiceUsesMinimalContractAndStoresCompleteMessage() {
		var store = new TestSessionStateStore();
		var conversations = new RecordingConversationService();
		var service = new FreeChatSessionService(
				store,
				new TestAuthService(),
				new NoOpUsageQuotaService(),
				new FixedSessionIdGenerator(),
				conversations);

		var response = service.startSession("system prompt");
		byte[] audio = {1, 2, 3};
		service.addMessage(new Message(1, "Hello", audio));
		service.endSession(response.sessionId(), "2026-07-28T09:00:00Z");

		assertEquals("scene-session-1", response.sessionId());
		assertEquals("system prompt", store.sessions.get(response.sessionId()).getPrompt().systemPrompt());
		assertEquals("Hello", conversations.messages.getFirst().text());
		assertArrayEquals(audio, conversations.messages.getFirst().audio());
		assertEquals(1, store.sessions.get(response.sessionId()).getMessages().size());
		assertEquals(
				Instant.parse("2026-07-28T09:00:00Z"),
				store.sessions.get(response.sessionId()).getEndedAt());
	}

	@Test
	void realtimeConnectorPassesConnectionFieldsAndReturnsAnswerSdp() {
		var store = new TestSessionStateStore();
		var quota = new NoOpUsageQuotaService();
		var sessionService = new FreeChatSessionService(
				store,
				new TestAuthService(),
				quota,
				new FixedSessionIdGenerator(),
				new RecordingConversationService());
		var session = sessionService.startSession("system prompt");
		var connectionService = new RecordingConnectionService();
		Instant expiresAt = Instant.parse("2026-07-28T09:05:00Z");
		connectionService.result = new RealtimeConnectionResult(
				"provider-session-1",
				"answer-sdp",
				expiresAt);
		var connector = new RealtimeSessionConnector(store, connectionService, quota);

		var result = connector.connect(
				session.sessionId(),
				"scene-1",
				"system prompt",
				new StartSceneSessionRequest(
						null,
						SceneType.FREE_CHAT,
						"weekend travel",
						"",
						"offer-sdp",
						ProviderType.QWEN,
						"qwen3.5-omni-flash-realtime",
						"Katerina",
						true));

		assertEquals(ProviderType.QWEN, connectionService.providerType);
		assertEquals("offer-sdp", connectionService.command.offerSdp());
		assertEquals("qwen3.5-omni-flash-realtime", connectionService.command.model());
		assertEquals("Katerina", connectionService.command.voice());
		assertEquals("answer-sdp", result.answerSdp());
		assertEquals(expiresAt, result.credentialExpiresAt());
		assertEquals(SessionStatus.WAITING_CLIENT, result.status());
	}

	private static final class TestAuthService implements AuthService {
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
			return "user-1";
		}
	}

	private static final class RecordingConnectionService implements RealtimeConnectionService {
		private ProviderType providerType;
		private StartCommand command;
		private RealtimeConnectionResult result;

		@Override
		public RealtimeConnectionResult connect(
				ProviderType type,
				AbstractSceneSession session,
				SessionPrompt prompt,
				StartCommand command) {
			this.providerType = type;
			this.command = command;
			return result;
		}
	}

	private static final class FixedSessionIdGenerator extends SessionIdGenerator {
		@Override
		public String generate() {
			return "scene-session-1";
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
