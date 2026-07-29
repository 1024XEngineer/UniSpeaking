package com.unispeaking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.unispeaking.component.SessionIdGenerator;
import com.unispeaking.domain.dto.command.StartCommand;
import com.unispeaking.domain.dto.session.AddSessionMessageRequest;
import com.unispeaking.domain.dto.session.StartSessionRequest;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.po.session.FreeChatSceneSession;
import com.unispeaking.domain.vo.prompt.SessionPrompt;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.realtime.RealtimeConnectionResult;
import com.unispeaking.domain.vo.realtime.RealtimeCredential;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.repository.SessionStateStore;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.RealtimeProvider;
import com.unispeaking.service.quota.UsageQuotaService;
import com.unispeaking.service.realtime.RealtimeConnectionService;
import com.unispeaking.service.realtime.RealtimeCredentialService;
import com.unispeaking.service.realtime.impl.RealtimeConnectionServiceImpl;
import com.unispeaking.service.session.SessionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class SessionRealtimeConnectionTest {

	@Test
	void passesRealtimeFieldsToConnectionServiceAndReturnsAnswerSdp() {
		RecordingConnectionService connectionService = new RecordingConnectionService();
		Instant expiresAt = Instant.parse("2026-07-27T15:05:00Z");
		connectionService.result = new RealtimeConnectionResult(null, "answer-sdp", expiresAt);

		SessionService service = new SessionService(
				new TestSessionStateStore(),
				requestedUserId -> requestedUserId,
				new NoOpUsageQuotaService(),
				new FixedSessionIdGenerator(),
				connectionService) {
			@Override
			protected AbstractSceneSession createSession(String sessionId, String userId) {
				return new FreeChatSceneSession(sessionId, userId);
			}

			@Override
			protected SessionPrompt prepareScene(
					AbstractSceneSession session,
					StartSessionRequest request) {
				return new SessionPrompt(request.prompt());
			}

			@Override
			protected void appendMessage(
					AbstractSceneSession session,
					AddSessionMessageRequest request) {
			}

			@Override
			protected void handleSessionCompleted(AbstractSceneSession session) {
			}
		};

		var response = service.startSession(new StartSessionRequest(
				"user-1",
				"scene-1",
				"flow-1",
				SceneType.FREE_CHAT,
				"system prompt",
				"offer-sdp",
				ProviderType.QWEN,
				"qwen3.5-omni-flash-realtime",
				"Katerina",
				true));

		assertEquals(ProviderType.QWEN, connectionService.providerType);
		assertEquals("offer-sdp", connectionService.command.offerSdp());
		assertEquals("qwen3.5-omni-flash-realtime", connectionService.command.model());
		assertEquals("Katerina", connectionService.command.voice());
		assertEquals("answer-sdp", response.answerSdp());
		assertEquals(expiresAt, response.credentialExpiresAt());
		assertEquals(SessionStatus.WAITING_CLIENT, response.status());
	}

	@Test
	void realtimeConnectionUsesTheConfiguredFallbackProviderWithoutExposingItToTheCaller() {
		FailingRealtimeProvider primary = new FailingRealtimeProvider();
		SuccessfulRealtimeProvider fallback = new SuccessfulRealtimeProvider();
		AiProviderRegistry registry = new AiProviderRegistry(
				List.of(primary, fallback),
				List.of(),
				List.of(),
				List.of(),
				List.of(),
				Map.of(
						com.unispeaking.domain.vo.ai.AiCapability.REALTIME,
						List.of(
								FailingRealtimeProvider.MODEL_ID,
								SuccessfulRealtimeProvider.MODEL_ID)));
		RecordingCredentialService credentials = new RecordingCredentialService();
		RealtimeConnectionService service = new RealtimeConnectionServiceImpl(
				registry,
				credentials);
		FreeChatSceneSession session = new FreeChatSceneSession("session-1", "user-1");

		RealtimeConnectionResult result = service.connect(
				null,
				session,
				new SessionPrompt("system prompt"),
				new StartCommand(
						SceneType.FREE_CHAT,
						"user-1",
						"scene-1",
						"offer-sdp",
						null,
						null,
						null,
						null,
						false));

		assertEquals("fallback-answer", result.answerSdp());
		assertEquals(
				List.of(ProviderType.QWEN, ProviderType.OPENAI),
				credentials.requestedProviders);
		assertEquals(1, primary.calls);
		assertEquals(1, fallback.calls);
		assertEquals(SuccessfulRealtimeProvider.MODEL_ID, fallback.modelId);
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

	private static final class FailingRealtimeProvider extends RealtimeProvider {

		private static final String MODEL_ID = "primary-realtime";
		private int calls;

		private FailingRealtimeProvider() {
			super(ProviderType.QWEN, Set.of(MODEL_ID));
		}

		@Override
		public String exchangeRealtimeSdp(
				String modelId,
				String offerSdp,
				String token) {
			calls++;
			throw new BusinessException(
					"QWEN_SIGNALING_IO_ERROR",
					"primary unavailable");
		}
	}

	private static final class SuccessfulRealtimeProvider extends RealtimeProvider {

		private static final String MODEL_ID = "fallback-realtime";
		private int calls;
		private String modelId;

		private SuccessfulRealtimeProvider() {
			super(ProviderType.OPENAI, Set.of(MODEL_ID));
		}

		@Override
		public String exchangeRealtimeSdp(
				String modelId,
				String offerSdp,
				String token) {
			calls++;
			this.modelId = modelId;
			return "fallback-answer";
		}
	}

	private static final class RecordingCredentialService implements RealtimeCredentialService {

		private final List<ProviderType> requestedProviders = new ArrayList<>();

		@Override
		public RealtimeCredential getCredential(ProviderType providerType) {
			requestedProviders.add(providerType);
			return new RealtimeCredential(providerType.name().toLowerCase() + "-token", null);
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
