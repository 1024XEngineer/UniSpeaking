package com.unispeaking.infrastructure.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.domain.dto.session.StartCommand;
import com.unispeaking.domain.po.session.CustomSceneSession;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.domain.vo.session.RealtimeCredential;
import com.unispeaking.domain.vo.session.SessionPrompt;
import com.unispeaking.provider.AiInvocationContext;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.RealtimeProvider;
import java.time.Instant;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class RealtimeSdpExchangeTest {

	@Test
	void issuesCredentialAndMapsAllConnectionFields() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		RealtimeCredentialIssuer issuer = mock(RealtimeCredentialIssuer.class);
		RealtimeProvider provider = new CredentialProvider();
		RealtimeCredential credential = new RealtimeCredential(
				"temporary-token", Instant.parse("2026-08-21T00:00:00Z"));
		when(issuer.issue(ProviderType.QWEN)).thenReturn(credential);
		RealtimeConnectionResult expected = new RealtimeConnectionResult(
				"provider-session", ProviderType.QWEN, "model-1", "Tina", "trace-1",
				"answer-sdp", credential.expiresAt());
		whenExchangeInvokesOperation(registry, ProviderType.QWEN, "model-1", provider, expected);

		CustomSceneSession session = new CustomSceneSession("session-1", "user-1");
		StartCommand command = new StartCommand(
				SceneType.CUSTOM_SCENE, "user-1", "scene-1", "offer-sdp", "topic",
				ProviderType.QWEN, "model-1", "Tina", true);
		RealtimeConnectionResult result = new RealtimeSdpExchange(registry, issuer)
				.exchangeSdp(ProviderType.QWEN, session, new SessionPrompt("prompt"), command);

		assertEquals(expected, result);
		verify(issuer).issue(ProviderType.QWEN);
	}

	@Test
	void skipsCredentialIssuerForProviderWithServerSideCredential() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		RealtimeCredentialIssuer issuer = mock(RealtimeCredentialIssuer.class);
		RealtimeProvider provider = new ServerCredentialProvider();
		RealtimeConnectionResult expected = new RealtimeConnectionResult(
				"provider-session", ProviderType.QINIU, "model-2", "Margaret", "trace-2",
				"answer", null);
		whenExchangeInvokesOperation(registry, ProviderType.QINIU, "model-2", provider, expected);

		CustomSceneSession session = new CustomSceneSession("session-2", "user-2");
		StartCommand command = new StartCommand(
				SceneType.CUSTOM_SCENE, "user-2", "scene-2", "offer", null,
				ProviderType.QINIU, "model-2", "Margaret", false);

		assertEquals(expected, new RealtimeSdpExchange(registry, issuer)
				.exchangeSdp(ProviderType.QINIU, session, new SessionPrompt("prompt"), command));
		verifyNoInteractions(issuer);
	}

	@Test
	void propagatesCredentialProviderAndRealtimeFailuresWithoutChangingThem() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		RealtimeCredentialIssuer issuer = mock(RealtimeCredentialIssuer.class);
		BusinessException credentialFailure = new BusinessException(
				"CREDENTIAL_FAILED", "credential unavailable");
		when(issuer.issue(ProviderType.QWEN)).thenThrow(credentialFailure);
		doAnswer(invocation -> {
				@SuppressWarnings("rawtypes")
				BiFunction operation = (BiFunction) invocation.getArgument(3);
				return operation.apply("model-1", new CredentialProvider());
			}).when(registry).routeRealtime(
				any(AiInvocationContext.class), eq(ProviderType.QWEN), eq("model-1"),
				anyBiFunction());
		CustomSceneSession session = new CustomSceneSession("session-3", "user-3");
		StartCommand command = command(ProviderType.QWEN, "model-1");

		assertSame(credentialFailure, assertThrows(BusinessException.class, () ->
				new RealtimeSdpExchange(registry, issuer)
						.exchangeSdp(ProviderType.QWEN, session, new SessionPrompt("prompt"), command)));

		RuntimeException connectionFailure = new IllegalStateException("connect failed");
		doReturn(new RealtimeCredential("token", null)).when(issuer).issue(ProviderType.QWEN);
		doAnswer(invocation -> {
				@SuppressWarnings("rawtypes")
				BiFunction operation = (BiFunction) invocation.getArgument(3);
				throw connectionFailure;
			}).when(registry).routeRealtime(
				any(AiInvocationContext.class), eq(ProviderType.QWEN), eq("model-1"),
				anyBiFunction());
		assertSame(connectionFailure, assertThrows(RuntimeException.class, () ->
				new RealtimeSdpExchange(registry, issuer)
						.exchangeSdp(ProviderType.QWEN, session, new SessionPrompt("prompt"), command)));
	}

	private void whenExchangeInvokesOperation(
			AiProviderRegistry registry,
			ProviderType type,
			String model,
			RealtimeProvider provider,
			RealtimeConnectionResult expected) {
		doAnswer(invocation -> {
			@SuppressWarnings("rawtypes")
			BiFunction operation = (BiFunction) invocation.getArgument(3);
			return operation.apply(model, provider);
		}).when(registry).routeRealtime(
				any(AiInvocationContext.class), eq(type), eq(model), anyBiFunction());
	}

	@SuppressWarnings("unchecked")
	private BiFunction<String, RealtimeProvider, RealtimeConnectionResult> anyBiFunction() {
		return (BiFunction<String, RealtimeProvider, RealtimeConnectionResult>) any(BiFunction.class);
	}

	private StartCommand command(ProviderType type, String model) {
		return new StartCommand(
				SceneType.CUSTOM_SCENE, "user-3", "scene-3", "offer", null,
				type, model, "Tina", true);
	}

	private static final class CredentialProvider extends RealtimeProvider {
		private CredentialProvider() {
			super(ProviderType.QWEN, Set.of("model-1"));
		}

		@Override
		public String exchangeRealtimeSdp(String modelId, String offerSdp, String token) {
			return "unused";
		}

		@Override
		public RealtimeConnectionResult connect(
				RealtimeConnectCommand command, RealtimeCredential credential) {
			assertEquals("model-1", command.modelId());
			assertEquals("offer-sdp", command.offerSdp());
			assertEquals("user-1", command.userId());
			assertEquals("session-1", command.clientId());
			assertEquals("scene-1", command.sceneId());
			assertEquals(SceneType.CUSTOM_SCENE, command.sceneType());
			assertEquals("Tina", command.voiceId());
			assertEquals("temporary-token", credential.bearerToken());
			return new RealtimeConnectionResult(
					"provider-session", ProviderType.QWEN, command.modelId(), command.voiceId(),
					"trace-1", "answer-sdp", credential.expiresAt());
		}
	}

	private static final class ServerCredentialProvider extends RealtimeProvider {
		private ServerCredentialProvider() {
			super(ProviderType.QINIU, Set.of("model-2"));
		}

		@Override
		public String exchangeRealtimeSdp(String modelId, String offerSdp, String token) {
			return "unused";
		}

		@Override
		public boolean requiresIssuedCredential() {
			return false;
		}

		@Override
		public RealtimeConnectionResult connect(
				RealtimeConnectCommand command, RealtimeCredential credential) {
			assertEquals("", credential.bearerToken());
			assertEquals(null, credential.expiresAt());
			return new RealtimeConnectionResult(
					"provider-session", ProviderType.QINIU, command.modelId(), command.voiceId(),
					"trace-2", "answer", null);
		}
	}
}
