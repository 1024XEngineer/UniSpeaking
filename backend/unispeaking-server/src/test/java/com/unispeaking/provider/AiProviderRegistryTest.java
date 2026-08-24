package com.unispeaking.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.provider.AiModelDefinition;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.provider.config.AiConfigurationStore;
import com.unispeaking.provider.config.AiModelConfiguration;
import com.unispeaking.provider.config.AiProviderConfiguration;
import com.unispeaking.provider.config.AiProviderCredentialStore;
import com.unispeaking.provider.config.AiRuntimeConfiguration;
import com.unispeaking.provider.usage.AiInvocationAttempt;
import com.unispeaking.provider.usage.AiInvocationLedger;
import com.unispeaking.service.auth.AuthService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AiProviderRegistryTest {

	@Test
	void disablesAllQiniuAiModelsAtRegistrationBoundary() {
		AiProviderRegistry registry = new AiProviderRegistry(
				List.of(new StubQiniuRealtimeProvider(), new StubRealtimeProvider()),
				llmProviders(),
				List.of(new StubScoringProvider()),
				ttsProviders(),
				List.of(new StubTranscriptionProvider()),
				Map.of(
						AiCapability.REALTIME, List.of(
								AiProviderRegistry.QINIU_REALTIME_PLUS,
								AiProviderRegistry.QWEN_REALTIME_FLASH),
						AiCapability.LLM, List.of(
								AiProviderRegistry.QINIU_MAAS_QWEN_PLUS,
								AiProviderRegistry.QWEN_LLM_PLUS,
								AiProviderRegistry.DEEPSEEK_CHAT)),
				false);

		assertEquals(
				List.of(AiProviderRegistry.QWEN_REALTIME_FLASH),
				registry.route(AiCapability.REALTIME));
		assertEquals(
				List.of(
						AiProviderRegistry.QWEN_LLM_PLUS,
						AiProviderRegistry.DEEPSEEK_CHAT),
				registry.route(AiCapability.LLM));
		assertFalse(registry.deployedModels().stream()
				.anyMatch(model -> model.providerId().startsWith("qiniu")));
	}

	@Test
	void exposesOnlyTheDocumentedProviderOperations() {
		assertEquals(
				Set.of(
						"exchangeRealtimeSdp",
						"generateSpeechAudio",
						"executeLlmTask",
						"convertAudioToText",
						"evaluatePronunciation"),
				List.of(AiProvider.class.getDeclaredMethods()).stream()
						.filter(method -> !method.isSynthetic())
						.map(java.lang.reflect.Method::getName)
						.collect(java.util.stream.Collectors.toSet()));
		assertTrue(
				List.of(AiModelDefinition.class.getRecordComponents()).stream()
						.anyMatch(component -> component.getName().equals("providerId")
								&& component.getType() == String.class));
	}

	@Test
	void selectsProvidersByCapabilityAndModel() {
		StubRealtimeProvider realtime = new StubRealtimeProvider();
		StubQiniuRealtimeProvider qiniu = new StubQiniuRealtimeProvider();
		AiProviderRegistry registry = realtimeRegistry(qiniu, realtime);

		assertEquals(
				AiProviderRegistry.QINIU_REALTIME_PLUS,
				registry.defaultModel(AiCapability.REALTIME));
		assertEquals(
				AiProviderRegistry.QINIU_MAAS_QWEN_PLUS,
				registry.defaultModel(AiCapability.LLM));
		assertSame(
				qiniu,
				registry.getRealtimeProvider(AiProviderRegistry.QINIU_REALTIME_PLUS));
		assertSame(
				realtime,
				registry.getRealtimeProvider(AiProviderRegistry.QWEN_REALTIME_FLASH));
		assertEquals(
				"answer",
				registry.exchangeRealtimeSdp(
						AiProviderRegistry.QWEN_REALTIME_FLASH,
						"offer",
						"key"));
	}

	@Test
	void rejectsCapabilityMismatchAndDuplicateModelRegistration() {
		AiProviderRegistry registry = registry(new StubRealtimeProvider());

		BusinessException mismatch = assertThrows(
				BusinessException.class,
				() -> registry.getLlmProvider(AiProviderRegistry.QWEN_REALTIME_FLASH));
		assertEquals("AI_MODEL_CAPABILITY_MISMATCH", mismatch.code());

		assertThrows(
				IllegalStateException.class,
				() -> new AiProviderRegistry(
						List.of(new StubRealtimeProvider(), new StubRealtimeProvider()),
						llmProviders(),
						List.of(new StubScoringProvider()),
						ttsProviders(),
						List.of()));
	}

	@Test
	void routesAFeatureCallThroughTheConfiguredPrimaryModel() {
		StubQwenLlmProvider qwen = new StubQwenLlmProvider();
		StubDeepSeekLlmProvider deepSeek = new StubDeepSeekLlmProvider();
		AiProviderRegistry registry = registry(
				List.of(qwen, deepSeek),
				Map.of(AiCapability.LLM, List.of(AiProviderRegistry.DEEPSEEK_CHAT)));

		String response = registry.executeLlmTask("hello", null);

		assertEquals("deepseek", response);
		assertEquals(0, qwen.calls);
		assertEquals(1, deepSeek.calls);
		assertEquals(AiProviderRegistry.DEEPSEEK_CHAT, registry.defaultModel(AiCapability.LLM));
	}

	@Test
	void failsOverFromQiniuQwenToAlibabaQwen() {
		FailingQiniuMaasLlmProvider primary = new FailingQiniuMaasLlmProvider(
				AiProviderRegistry.QINIU_MAAS_QWEN_PLUS);
		StubQwenLlmProvider alibabaQwen = new StubQwenLlmProvider();
		StubDeepSeekLlmProvider legacyDeepSeek = new StubDeepSeekLlmProvider();
		AiProviderRegistry registry = registry(
				List.of(primary, alibabaQwen, legacyDeepSeek),
				Map.of(
						AiCapability.LLM,
						List.of(
								AiProviderRegistry.QINIU_MAAS_QWEN_PLUS,
								AiProviderRegistry.QWEN_LLM_PLUS)));

		Logger logger = (Logger) LoggerFactory.getLogger(AiProviderRegistry.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		String response;
		try {
			response = registry.executeLlmTask("hello", null);
		}
		finally {
			logger.detachAppender(appender);
		}

		assertEquals("qwen", response);
		assertEquals(1, alibabaQwen.calls);
		assertEquals(0, legacyDeepSeek.calls);
		String logs = appender.list.stream()
				.map(ILoggingEvent::getFormattedMessage)
				.collect(java.util.stream.Collectors.joining("\n"));
		assertTrue(logs.contains("AI provider attempt capability=LLM"));
		assertTrue(logs.contains("AI provider failover capability=LLM"));
		assertTrue(logs.contains("durationMs="));
		assertTrue(logs.contains("nextModel=qwen3.5-plus"));
	}

	@Test
	void preservesProviderOrderWhenConfiguredModelsReplaceDefaultModelNames() {
		LlmProvider qwen = new ConfiguredModelLlmProvider("qwen", "qwen-custom-llm");
		LlmProvider deepSeek = new ConfiguredModelLlmProvider(
				"deepseek",
				"deepseek-custom-llm");

		AiProviderRegistry registry = registry(List.of(deepSeek, qwen), Map.of());

		assertEquals("qwen-custom-llm", registry.defaultModel(AiCapability.LLM));
		assertEquals(
				List.of("qwen-custom-llm", "deepseek-custom-llm"),
				registry.route(AiCapability.LLM));
		assertSame(qwen, registry.getLlmProvider("qwen-custom-llm"));
		assertSame(deepSeek, registry.getLlmProvider("deepseek-custom-llm"));
	}

	@Test
	void usesQiniuAsThePrimaryRealtimeAndLlmProvider() {
		AiProviderRegistry registry = realtimeRegistry(
				new StubQiniuRealtimeProvider(), new StubRealtimeProvider());

		assertEquals(
				AiProviderRegistry.QINIU_REALTIME_PLUS,
				registry.defaultModel(AiCapability.REALTIME));
		assertEquals(
				AiProviderRegistry.QINIU_MAAS_QWEN_PLUS,
				registry.defaultModel(AiCapability.LLM));
		assertEquals(
				StubTranscriptionProvider.MODEL_ID,
				registry.defaultModel(AiCapability.TRANSCRIPTION));
		assertEquals(
				AiProviderRegistry.QWEN_TTS,
				registry.defaultModel(AiCapability.TTS));
		assertEquals(
				AiProviderRegistry.IFLYTEK_PRONUNCIATION_SCORING,
				registry.defaultModel(AiCapability.SCORING));
		assertEquals(
				List.of(
						AiProviderRegistry.QINIU_MAAS_QWEN_PLUS,
						AiProviderRegistry.QWEN_LLM_PLUS),
				registry.route(AiCapability.LLM));
	}

	@Test
	void failsOverToTheNextConfiguredModelWhenThePrimaryProviderIsUnavailable() {
		StubQwenLlmProvider qwen = new StubQwenLlmProvider();
		qwen.failure = new BusinessException(
				"QWEN_LLM_IO_ERROR",
				"primary unavailable");
		StubDeepSeekLlmProvider deepSeek = new StubDeepSeekLlmProvider();
		AiProviderRegistry registry = registry(
				List.of(qwen, deepSeek),
				Map.of(
						AiCapability.LLM,
						List.of(
								AiProviderRegistry.QWEN_LLM_PLUS,
								AiProviderRegistry.DEEPSEEK_CHAT)));

		String response = registry.executeLlmTask("hello", null);

		assertEquals("deepseek", response);
		assertEquals(1, qwen.calls);
		assertEquals(1, deepSeek.calls);
	}

	@Test
	void doesNotFailOverWhenTheRequestItselfIsInvalid() {
		StubQwenLlmProvider qwen = new StubQwenLlmProvider();
		qwen.failure = new BusinessException("INVALID_LLM_PROMPT", "prompt is required");
		StubDeepSeekLlmProvider deepSeek = new StubDeepSeekLlmProvider();
		AiProviderRegistry registry = registry(
				List.of(qwen, deepSeek),
				Map.of(
						AiCapability.LLM,
						List.of(
								AiProviderRegistry.QWEN_LLM_PLUS,
								AiProviderRegistry.DEEPSEEK_CHAT)));

		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> registry.executeLlmTask("", null));

		assertEquals("INVALID_LLM_PROMPT", exception.code());
		assertEquals(1, qwen.calls);
		assertEquals(0, deepSeek.calls);
	}

	@Test
	void switchesScoringProviderByChangingOnlyTheConfiguredModelRoute() {
		StubScoringProvider iflytek = new StubScoringProvider();
		AlternativeScoringProvider alternative = new AlternativeScoringProvider();
		AiProviderRegistry registry = new AiProviderRegistry(
				List.of(new StubRealtimeProvider()),
				llmProviders(),
				List.of(iflytek, alternative),
				ttsProviders(),
				List.of(new StubTranscriptionProvider()),
				Map.of(
						AiCapability.REALTIME,
						List.of(AiProviderRegistry.QWEN_REALTIME_FLASH),
						AiCapability.LLM,
						List.of(AiProviderRegistry.QWEN_LLM_PLUS),
						AiCapability.SCORING,
						List.of(AlternativeScoringProvider.MODEL_ID),
						AiCapability.TTS,
						List.of(AiProviderRegistry.ALIYUN_TTS),
						AiCapability.TRANSCRIPTION,
						List.of(StubTranscriptionProvider.MODEL_ID)));

		String response = registry.evaluatePronunciation(
				"hello",
				new Byte[] {0},
				null);

		assertEquals("{\"totalScore\":99}", response);
		assertEquals(0, iflytek.calls);
		assertEquals(1, alternative.calls);
	}

	@Test
	void databaseRouteUsesAdapterTypeAndSkipsDisabledProviders() {
		StubQwenLlmProvider qwen = new StubQwenLlmProvider();
		StubDeepSeekLlmProvider deepSeek = new StubDeepSeekLlmProvider();
		AiProviderRegistry registry = registry(List.of(qwen, deepSeek), Map.of());
		registry.configureDynamicRuntime(
				() -> runtime(
						Map.of(
								"qwen-primary", provider("qwen-primary", "qwen", false),
								"deepseek-backup", provider("deepseek-backup", "deepseek", true)),
						Map.of(
								AiProviderRegistry.QWEN_LLM_PLUS,
								model(AiProviderRegistry.QWEN_LLM_PLUS, "qwen-primary"),
								AiProviderRegistry.DEEPSEEK_CHAT,
								model(AiProviderRegistry.DEEPSEEK_CHAT, "deepseek-backup"))),
				attempt -> {},
				new AuthService() {
					@Override public com.unispeaking.domain.dto.auth.AuthResponse register(com.unispeaking.domain.dto.auth.RegisterRequest request) { return null; }
					@Override public com.unispeaking.domain.dto.auth.AuthResponse login(com.unispeaking.domain.dto.auth.LoginRequest request) { return null; }
					@Override public com.unispeaking.domain.dto.auth.UserAccountResponse currentUser() { return null; }
					@Override public String requireUserId(String requestedUserId) { return requestedUserId; }
				},
				credentials());

		assertEquals(List.of(AiProviderRegistry.DEEPSEEK_CHAT), registry.route(AiCapability.LLM));
		assertEquals("deepseek", registry.executeLlmTask("hello", null));
		assertEquals(0, qwen.calls);
		assertEquals(1, deepSeek.calls);
	}

	@Test
	void recordsFailedPrimaryAndSuccessfulFallbackAgainstTheSameUserRequest() {
		StubQwenLlmProvider qwen = new StubQwenLlmProvider();
		qwen.failure = new BusinessException("QWEN_LLM_IO_ERROR", "primary unavailable");
		StubDeepSeekLlmProvider deepSeek = new StubDeepSeekLlmProvider();
		AiProviderRegistry registry = registry(List.of(qwen, deepSeek), Map.of());
		List<AiInvocationAttempt> attempts = new ArrayList<>();
		registry.configureDynamicRuntime(
				() -> runtime(
						Map.of(
								"qwen", provider("qwen", "qwen", true),
								"deepseek", provider("deepseek", "deepseek", true)),
						Map.of(
								AiProviderRegistry.QWEN_LLM_PLUS,
								model(AiProviderRegistry.QWEN_LLM_PLUS, "qwen"),
								AiProviderRegistry.DEEPSEEK_CHAT,
								model(AiProviderRegistry.DEEPSEEK_CHAT, "deepseek"))),
				attempts::add,
				null,
				credentials());
		UUID requestId = UUID.randomUUID();
		String userId = "11111111-1111-4111-8111-111111111111";
		AiInvocationContext context = new AiInvocationContext(
				requestId, userId, "session-1", "evaluation", "default");

		assertEquals("deepseek", registry.executeLlmTaskRouted(context, "hello", null).response());
		assertEquals(2, attempts.size());
		assertEquals(List.of("FAILED", "SUCCEEDED"), attempts.stream().map(AiInvocationAttempt::status).toList());
		assertTrue(attempts.getFirst().retryable());
		assertEquals(AiProviderRegistry.QWEN_LLM_PLUS, attempts.get(1).fallbackFromModelId());
		assertTrue(attempts.stream().allMatch(attempt -> attempt.context().logicalRequestId().equals(requestId)));
		assertTrue(attempts.stream().allMatch(attempt -> userId.equals(attempt.context().userId())));
	}

	@Test
	void recordsRealtimeProviderRequestIdInInvocationLedger() {
		AiProviderRegistry registry = registry(new StubRealtimeProvider());
		List<AiInvocationAttempt> attempts = new ArrayList<>();
		registry.configureDynamicRuntime(null, attempts::add, null, null);

		registry.recordRealtimeSession(
				"11111111-1111-4111-8111-111111111111",
				"session-1",
				AiProviderRegistry.QWEN_REALTIME_FLASH,
				"official-request-01",
				Instant.parse("2026-08-18T08:00:00Z"),
				Instant.parse("2026-08-18T08:01:00Z"));

		assertEquals(1, attempts.size());
		assertEquals("official-request-01", attempts.getFirst().providerRequestId());
		assertEquals("11111111-1111-4111-8111-111111111111", attempts.getFirst().context().userId());
	}

	@Test
	void recordsMeteredTtsFailureBeforeFallingBack() {
		AiProviderRegistry registry = new AiProviderRegistry(
				List.of(new StubRealtimeProvider()),
				llmProviders(),
				List.of(new StubScoringProvider()),
				List.of(new MeteredFailingQwenTtsProvider(), new SuccessfulAliyunTtsProvider()),
				List.of(new StubTranscriptionProvider()),
				Map.of(AiCapability.TTS, List.of(
						AiProviderRegistry.QWEN_TTS,
						AiProviderRegistry.ALIYUN_TTS)));
		List<AiInvocationAttempt> attempts = new ArrayList<>();
		registry.configureDynamicRuntime(null, attempts::add, null, null);
		AiInvocationContext context = new AiInvocationContext(
				UUID.randomUUID(),
				"11111111-1111-4111-8111-111111111111",
				"session-tts",
				"tts",
				"default");

		registry.generateSpeechAudioRouted(context, "Hello", null);

		assertEquals(2, attempts.size());
		AiInvocationAttempt failed = attempts.getFirst();
		assertEquals("FAILED", failed.status());
		assertEquals("QWEN_TTS_AUDIO_DOWNLOAD_FAILED", failed.errorCode());
		assertEquals("qwen-billed-request", failed.providerRequestId());
		assertEquals(5, failed.usage().inputCharacters());
		assertTrue(failed.retryable());
		assertEquals("SUCCEEDED", attempts.get(1).status());
	}

	@Test
	void rejectsDisabledRuntimeModelsInsteadOfRoutingToTheirAdapters() {
		StubQwenLlmProvider qwen = new StubQwenLlmProvider();
		AiProviderRegistry registry = registry(List.of(qwen), Map.of(
				AiCapability.LLM, List.of(AiProviderRegistry.QWEN_LLM_PLUS)));
		registry.configureDynamicRuntime(
				() -> runtime(
						Map.of("qwen", provider("qwen", "qwen", true)),
						Map.of(AiProviderRegistry.QWEN_LLM_PLUS, new AiModelConfiguration(
								AiProviderRegistry.QWEN_LLM_PLUS,
								"qwen",
								AiProviderRegistry.QWEN_LLM_PLUS,
								AiCapability.LLM,
								false,
								"TOKENS",
								BigDecimal.ZERO,
								BigDecimal.ZERO,
								BigDecimal.ZERO,
								BigDecimal.ZERO,
								BigDecimal.ZERO,
								BigDecimal.ZERO,
								"CNY"))),
				attempt -> {},
				null,
				credentials());

		assertTrue(registry.models().isEmpty());
		assertEquals("AI_MODEL_NOT_AVAILABLE", assertThrows(
				BusinessException.class,
				() -> registry.getModel(AiProviderRegistry.QWEN_LLM_PLUS)).code());
		assertEquals("AI_PROVIDER_ROUTE_NOT_FOUND", assertThrows(
				BusinessException.class,
				() -> registry.route(AiCapability.LLM)).code());
		assertEquals(0, qwen.calls);
	}

	@Test
	void usesRuntimeCredentialForAnExplicitModelRequest() {
		StubQwenLlmProvider qwen = new StubQwenLlmProvider();
		AiProviderRegistry registry = registry(List.of(qwen), Map.of(
				AiCapability.LLM, List.of(AiProviderRegistry.QWEN_LLM_PLUS)));
		registry.configureDynamicRuntime(null, attempt -> {}, null, new AiProviderCredentialStore() {
			@Override public String credentialOrFallback(String providerId, String fallback) {
				return "runtime-secret";
			}
			@Override public String credentialOrFallback(String providerId, String field, String fallback) {
				return credentialOrFallback(providerId, fallback);
			}
			@Override public Map<String, String> credentialsOrFallback(String providerId, Map<String, String> fallback) {
				return Map.of("apiKey", "runtime-secret");
			}
			@Override public CredentialStatus status(String providerId) {
				return new CredentialStatus(true, null, false, List.of());
			}
			@Override public CredentialStatus replace(String providerId, Map<String, String> values) {
				throw new UnsupportedOperationException();
			}
		});

		assertEquals("qwen", registry.executeLlmTask(
				AiProviderRegistry.QWEN_LLM_PLUS, "prompt", null));
		assertEquals("runtime-secret", qwen.token);
	}

	@Test
	void rejectsExplicitRealtimeModelThatDoesNotBelongToTheRequestedProvider() {
		AiProviderRegistry registry = registry(new StubRealtimeProvider());

		assertEquals("AI_PROVIDER_MODEL_MISMATCH", assertThrows(
				BusinessException.class,
				() -> registry.routeRealtime(
						ProviderType.DEEPSEEK,
						AiProviderRegistry.QWEN_REALTIME_FLASH,
						(modelId, provider) -> "unused")).code());
		assertEquals("AI_TTS_MODEL_REQUIRED", assertThrows(
				BusinessException.class,
				() -> registry.generateSpeechAudioBytes(
						(String) null, "hello", null, "Katerina")).code());
	}

	@Test
	void recordsAndRethrowsTheLastRetryableFailureWhenAllCandidatesAreExhausted() {
		StubQwenLlmProvider qwen = new StubQwenLlmProvider();
		qwen.failure = new BusinessException("QWEN_LLM_IO_ERROR", "qwen offline");
		StubDeepSeekLlmProvider deepSeek = new StubDeepSeekLlmProvider();
		deepSeek.failure = new BusinessException("DEEPSEEK_LLM_IO_ERROR", "deepseek offline");
		AiProviderRegistry registry = registry(List.of(qwen, deepSeek), Map.of(
				AiCapability.LLM,
				List.of(AiProviderRegistry.QWEN_LLM_PLUS, AiProviderRegistry.DEEPSEEK_CHAT)));
		List<AiInvocationAttempt> attempts = new ArrayList<>();
		registry.configureDynamicRuntime(null, attempts::add, null, credentials());

		BusinessException failure = assertThrows(
				BusinessException.class,
				() -> registry.executeLlmTask("prompt", null));

		assertEquals("DEEPSEEK_LLM_IO_ERROR", failure.code());
		assertEquals(List.of("FAILED", "FAILED"), attempts.stream()
				.map(AiInvocationAttempt::status).toList());
		assertEquals(AiProviderRegistry.QWEN_LLM_PLUS, attempts.get(1).fallbackFromModelId());
	}

	@Test
	void exposesEnabledRuntimeModelsAndFallsBackToTheDefaultNamedRoute() {
		StubQwenLlmProvider qwen = new StubQwenLlmProvider();
		AiProviderRegistry registry = registry(List.of(qwen), Map.of(
				AiCapability.LLM, List.of(AiProviderRegistry.QWEN_LLM_PLUS)));
		registry.configureDynamicRuntime(
				() -> runtime(
						Map.of("qwen", provider("qwen", "qwen", true)),
						Map.of(AiProviderRegistry.QWEN_LLM_PLUS,
								model(AiProviderRegistry.QWEN_LLM_PLUS, "qwen"))),
				attempt -> {},
				null,
				credentials());

		AiModelDefinition available = registry.models().getFirst();
		assertEquals(AiProviderRegistry.QWEN_LLM_PLUS, available.modelId());
		assertTrue(available.defaultModel());
		assertEquals(available, registry.getModel("  QWEN3.5-PLUS  "));
		assertEquals(List.of(AiProviderRegistry.QWEN_LLM_PLUS),
				registry.route("evaluation", AiCapability.LLM));
	}

	@Test
	void routesDefaultRealtimeTranscriptionAndScoringOperationsThroughRegisteredAdapters() {
		AiProviderRegistry registry = registry(new StubRealtimeProvider());

		assertEquals("answer", registry.exchangeRealtimeSdp("offer", "credential"));
		assertEquals("transcript", registry.convertAudioToText(new Byte[] {1, 2}, null));
		assertEquals("{}", registry.evaluatePronunciation("hello", new Byte[] {1}, null));
		assertEquals("AI_MODEL_NOT_FOUND", assertThrows(
				BusinessException.class,
				() -> registry.getModel("missing-model")).code());
	}

	@Test
	void ignoresIncompleteRealtimeUsageAndClampsClockSkewToZero() {
		AiProviderRegistry registry = registry(new StubRealtimeProvider());
		List<AiInvocationAttempt> attempts = new ArrayList<>();
		registry.configureDynamicRuntime(null, attempts::add, null, null);

		registry.recordRealtimeSession(
				"user", "", AiProviderRegistry.QWEN_REALTIME_FLASH, "request", Instant.now(), Instant.now());
		registry.recordRealtimeSession(
				"user",
				"session-clock-skew",
				AiProviderRegistry.QWEN_REALTIME_FLASH,
				"request",
				Instant.parse("2026-08-21T08:01:00Z"),
				Instant.parse("2026-08-21T08:00:00Z"));

		assertEquals(1, attempts.size());
		assertEquals(0L, attempts.getFirst().durationMs());
		assertEquals(0D, attempts.getFirst().usage().audioInputSeconds());
	}

	@Test
	void reportsMissingRoutesAndModelsWhenNoAdaptersAreDeployed() {
		AiProviderRegistry registry = new AiProviderRegistry(
				null, null, null, null, null);

		assertTrue(registry.models().isEmpty());
		assertTrue(registry.deployedModels().isEmpty());
		assertEquals("AI_DEFAULT_MODEL_NOT_FOUND", assertThrows(
				BusinessException.class,
				() -> registry.defaultModel(AiCapability.LLM)).code());
		assertEquals("AI_PROVIDER_ROUTE_NOT_FOUND", assertThrows(
				BusinessException.class,
				() -> registry.route("missing-route", AiCapability.LLM)).code());
		assertEquals("AI_MODEL_NOT_FOUND", assertThrows(
				BusinessException.class,
				() -> registry.getModel(null)).code());
	}

	@Test
	void rejectsConfiguredRoutesThatReferenceAnUnavailableModel() {
		assertThrows(
				IllegalStateException.class,
				() -> new AiProviderRegistry(
						List.of(new StubRealtimeProvider()),
						llmProviders(),
						List.of(new StubScoringProvider()),
						ttsProviders(),
						List.of(new StubTranscriptionProvider()),
						Map.of(AiCapability.LLM, List.of("unknown-model"))));
	}

	@Test
	void filtersRuntimeModelsWithMissingDisabledOrMismatchedProviders() {
		AiProviderRegistry registry = registry(List.of(new StubQwenLlmProvider()), Map.of(
				AiCapability.LLM, List.of(AiProviderRegistry.QWEN_LLM_PLUS)));
		registry.configureDynamicRuntime(
				() -> runtime(
						Map.of(
								"enabled", provider("enabled", "qwen", true),
								"disabled", provider("disabled", "qwen", false),
								"wrong-adapter", provider("wrong-adapter", "deepseek", true)),
						Map.of(
								"enabled-model", model("enabled-model", "enabled"),
								"disabled-model", model("disabled-model", "disabled"),
								"wrong-adapter-model", model("wrong-adapter-model", "wrong-adapter"),
								"missing-provider-model", model("missing-provider-model", "missing"))),
				attempt -> {}, null, credentials());

		assertTrue(registry.models().isEmpty());
		assertEquals("AI_MODEL_NOT_AVAILABLE", assertThrows(
				BusinessException.class,
				() -> registry.getModel("wrong-adapter-model")).code());
		assertEquals("AI_PROVIDER_ROUTE_NOT_FOUND", assertThrows(
				BusinessException.class,
				() -> registry.route(AiCapability.LLM)).code());
	}

	@Test
	void routesExplicitAndAutomaticRequestsAcrossAllCapabilityAdapters() {
		StubQwenTtsProvider tts = new StubQwenTtsProvider();
		StubTranscriptionProvider asr = new StubTranscriptionProvider();
		AiProviderRegistry registry = new AiProviderRegistry(
				List.of(new StubRealtimeProvider()),
				llmProviders(),
				List.of(new StubScoringProvider()),
				List.of(tts),
				List.of(asr),
				Map.of(
						AiCapability.REALTIME, List.of(AiProviderRegistry.QWEN_REALTIME_FLASH),
						AiCapability.LLM, List.of(AiProviderRegistry.QWEN_LLM_PLUS),
						AiCapability.TTS, List.of(AiProviderRegistry.QWEN_TTS),
						AiCapability.TRANSCRIPTION, List.of(StubTranscriptionProvider.MODEL_ID),
						AiCapability.SCORING, List.of(AiProviderRegistry.IFLYTEK_PRONUNCIATION_SCORING)));

		assertEquals("answer", registry.exchangeRealtimeSdp(
				AiProviderRegistry.QWEN_REALTIME_FLASH, "offer", "token"));
		assertEquals("qwen", registry.executeLlmTask(
				AiProviderRegistry.QWEN_LLM_PLUS, "prompt", "token"));
		assertEquals("transcript", registry.convertAudioToText(
				StubTranscriptionProvider.MODEL_ID, new Byte[] {1}, "token"));
		assertEquals("{}", registry.evaluatePronunciation(
				AiProviderRegistry.IFLYTEK_PRONUNCIATION_SCORING,
				"hello", new Byte[] {1}, "token"));
		assertArrayEquals(new byte[] {1, 2}, registry.generateSpeechAudioBytes(
				AiProviderRegistry.QWEN_TTS, "hello", "token"));
		assertArrayEquals(new byte[] {1, 2}, registry.generateSpeechAudioBytes(
				AiProviderRegistry.QWEN_TTS, "hello", "token", "voice-a"));
		assertEquals(2, tts.calls);
		assertEquals(1, asr.calls);
	}

	@Test
	void rejectsNullAudioBytesAndRequiresModelForExplicitVoiceRequests() {
		AiProviderRegistry registry = registry(new StubRealtimeProvider());

		assertEquals("INVALID_AUDIO", assertThrows(
				BusinessException.class,
				() -> registry.evaluatePronunciation("hello", new Byte[] {null}, null)).code());
		assertEquals("AI_TTS_MODEL_REQUIRED", assertThrows(
				BusinessException.class,
				() -> registry.generateSpeechAudioBytes(" ", "hello", null, "voice-a")).code());
		assertEquals("AI_MODEL_NOT_FOUND", assertThrows(
				BusinessException.class,
				() -> registry.executeLlmTask("missing", "prompt", null)).code());
	}

	@Test
	void preservesRoutedResultMetadataAndScopedInvocationContext() {
		AiProviderRegistry registry = registry(new StubRealtimeProvider());
		AiInvocationContext context = new AiInvocationContext(
				UUID.randomUUID(), "user-1", "session-1", "practice", "default");
		List<AiInvocationAttempt> attempts = new ArrayList<>();
		registry.configureDynamicRuntime(null, attempts::add, null, credentials());

		AiProviderRegistry.RoutedResult<String> result =
				AiInvocationContexts.call(context,
						() -> registry.executeLlmTaskRouted("hello", null));

		assertEquals(AiProviderRegistry.QINIU_MAAS_QWEN_PLUS, result.modelId());
		assertEquals("qiniu-maas", result.providerId());
		assertEquals(AiCapability.LLM, result.capability());
		assertEquals("qiniu-maas", result.response());
		assertEquals(context, attempts.getFirst().context());
	}

	@Test
	void usesAuthenticatedUserWhenCreatingAnAutomaticContextAndIgnoresAuthFailures() {
		AiProviderRegistry registry = registry(new StubRealtimeProvider());
		List<AiInvocationAttempt> attempts = new ArrayList<>();
		registry.configureDynamicRuntime(
				null, attempts::add, new AuthService() {
					@Override public com.unispeaking.domain.dto.auth.UserAccountResponse currentUser() { return null; }
					@Override public String currentUserIdOrNull() { return "user-from-auth"; }
					@Override public com.unispeaking.domain.dto.auth.AuthResponse register(com.unispeaking.domain.dto.auth.RegisterRequest request) { return null; }
					@Override public com.unispeaking.domain.dto.auth.AuthResponse login(com.unispeaking.domain.dto.auth.LoginRequest request) { return null; }
					@Override public String requireUserId(String requestedUserId) { return requestedUserId; }
				}, credentials());

		registry.executeLlmTask("hello", null);
		assertEquals("user-from-auth", attempts.getFirst().context().userId());

		List<AiInvocationAttempt> failedAuthAttempts = new ArrayList<>();
		registry.configureDynamicRuntime(
				null, failedAuthAttempts::add, new AuthService() {
					@Override public com.unispeaking.domain.dto.auth.UserAccountResponse currentUser() { return null; }
					@Override public com.unispeaking.domain.dto.auth.AuthResponse register(com.unispeaking.domain.dto.auth.RegisterRequest request) { return null; }
					@Override public com.unispeaking.domain.dto.auth.AuthResponse login(com.unispeaking.domain.dto.auth.LoginRequest request) { return null; }
					@Override public String currentUserIdOrNull() {
						throw new IllegalStateException("outside request");
					}
					@Override public String requireUserId(String requestedUserId) { return requestedUserId; }
				}, credentials());
		registry.executeLlmTask("hello", null);
		assertEquals(null, failedAuthAttempts.getFirst().context().userId());
	}

	@Test
	void classifiesNonRetryableFailureCodesWithoutFailingOver() {
		List<String> nonRetryableCodes = List.of(
				"INVALID_LLM_PROMPT", "UNSUPPORTED_LLM_FORMAT",
				"QWEN_LLM_INTERRUPTED", "TTS_TEXT_TOO_LONG",
				"PRONUNCIATION_AUDIO_TOO_LARGE",
				"PRONUNCIATION_AUDIO_TOO_LONG",
				"PRONUNCIATION_REFERENCE_TOO_LONG");
		for (String code : nonRetryableCodes) {
			StubQwenLlmProvider primary = new StubQwenLlmProvider();
			primary.failure = new BusinessException(code, code);
			StubDeepSeekLlmProvider fallback = new StubDeepSeekLlmProvider();
			AiProviderRegistry registry = registry(
					List.of(primary, fallback),
					Map.of(AiCapability.LLM, List.of(
							AiProviderRegistry.QWEN_LLM_PLUS,
							AiProviderRegistry.DEEPSEEK_CHAT)));

			BusinessException actual = assertThrows(
					BusinessException.class,
					() -> registry.executeLlmTask("prompt", null));
			assertEquals(code, actual.code());
			assertEquals(0, fallback.calls);
		}
	}

	@Test
	void handlesNullCredentialStoreAndNullLedgerWithoutChangingRouting() {
		StubQwenLlmProvider qwen = new StubQwenLlmProvider();
		AiProviderRegistry registry = registry(List.of(qwen), Map.of(
				AiCapability.LLM, List.of(AiProviderRegistry.QWEN_LLM_PLUS)));
		registry.configureDynamicRuntime(null, null, null, null);

		assertEquals("qwen", registry.executeLlmTask(
				AiProviderRegistry.QWEN_LLM_PLUS, "prompt", "supplied-token"));
		registry.recordRealtimeSession(
				"user", "session", AiProviderRegistry.QWEN_REALTIME_FLASH,
				"request", Instant.now(), Instant.now());
		assertEquals("supplied-token", qwen.token);
	}

	@Test
	void parsesConfiguredEnvironmentRoutesAndNormalizesTheirModelIds() {
		AiProviderRegistry registry = new AiProviderRegistry(
				List.of(new StubRealtimeProvider()),
				List.of(new StubQwenLlmProvider(), new StubDeepSeekLlmProvider()),
				List.of(new StubScoringProvider()),
				ttsProviders(),
				List.of(new StubTranscriptionProvider()),
				"  " + AiProviderRegistry.QWEN_REALTIME_FLASH + " , "
						+ AiProviderRegistry.QWEN_REALTIME_FLASH,
				" qwen3.5-plus , DEEPSEEK-V4-FLASH ",
				" iflytek-suntone ",
				" qwen3-tts-flash ",
				" stub-asr ",
				true);

		assertEquals(List.of(AiProviderRegistry.QWEN_REALTIME_FLASH),
				registry.route(AiCapability.REALTIME));
		assertEquals(List.of(
				AiProviderRegistry.QWEN_LLM_PLUS,
				AiProviderRegistry.DEEPSEEK_CHAT), registry.route(AiCapability.LLM));
	}

	@Test
	void usesNamedDatabaseRouteOnlyWhenItIsAvailableAndFallsBackToDefaultPolicy() {
		StubQwenLlmProvider qwen = new StubQwenLlmProvider();
		StubDeepSeekLlmProvider deepSeek = new StubDeepSeekLlmProvider();
		AiProviderRegistry registry = registry(List.of(qwen, deepSeek), Map.of());
		registry.configureDynamicRuntime(
				() -> runtime(
						Map.of(
								"qwen", provider("qwen", "qwen", true),
								"deepseek", provider("deepseek", "deepseek", true)),
						Map.of(
								AiProviderRegistry.QWEN_LLM_PLUS,
								model(AiProviderRegistry.QWEN_LLM_PLUS, "qwen"),
								AiProviderRegistry.DEEPSEEK_CHAT,
								model(AiProviderRegistry.DEEPSEEK_CHAT, "deepseek")),
						Map.of(
								"evaluation", Map.of(AiCapability.LLM,
										List.of(AiProviderRegistry.DEEPSEEK_CHAT)),
								"default", Map.of(AiCapability.LLM,
										List.of(AiProviderRegistry.QWEN_LLM_PLUS)))),
				attempt -> {}, null, credentials());

		assertEquals(List.of(AiProviderRegistry.DEEPSEEK_CHAT),
				registry.route("evaluation", AiCapability.LLM));
		assertEquals(List.of(AiProviderRegistry.QWEN_LLM_PLUS),
				registry.route("unknown-route", AiCapability.LLM));
		assertEquals("deepseek", registry.executeLlmTask(
				new AiInvocationContext(null, null, null, "evaluation", "evaluation"),
				null, "prompt", null));
		assertEquals(0, qwen.calls);
		assertEquals(1, deepSeek.calls);
	}

	@Test
	void routesLlmResponseFormatAndAllConvenienceOverloads() {
		StubQwenLlmProvider qwen = new StubQwenLlmProvider();
		AiProviderRegistry registry = registry(List.of(qwen), Map.of(
				AiCapability.LLM, List.of(AiProviderRegistry.QWEN_LLM_PLUS)));

		assertEquals("qwen", registry.executeLlmTask(
				AiProviderRegistry.QWEN_LLM_PLUS, "prompt", "token",
				LlmResponseFormat.JSON_OBJECT));
		assertEquals("qwen", registry.executeLlmTaskRouted(
				"prompt", "token", LlmResponseFormat.TEXT).response());
		assertArrayEquals(new byte[] {1, 2}, registry.generateSpeechAudioBytes(
				"hello", "token"));
		assertArrayEquals(new Byte[] {1, 2}, registry.generateSpeechAudio(
				AiProviderRegistry.QWEN_TTS, "hello", "token"));
		assertEquals(2, qwen.calls);
	}

	@Test
	void failsOverRealtimeScoringAndTranscriptionRequests() {
		FailingRealtimeProvider realtime = new FailingRealtimeProvider(
				"realtime-primary", ProviderType.QWEN);
		SuccessfulRealtimeProvider realtimeFallback = new SuccessfulRealtimeProvider(
				"realtime-backup", ProviderType.OPENAI);
		FailingScoringProvider scoring = new FailingScoringProvider("scoring-primary");
		AlternativeScoringProvider scoringFallback = new AlternativeScoringProvider();
		FailingTranscriptionProvider transcription = new FailingTranscriptionProvider("asr-primary");
		AlternativeTranscriptionProvider transcriptionFallback =
				new AlternativeTranscriptionProvider("asr-backup");
		AiProviderRegistry registry = new AiProviderRegistry(
				List.of(realtime, realtimeFallback),
				llmProviders(),
				List.of(scoring, scoringFallback),
				ttsProviders(),
				List.of(transcription, transcriptionFallback),
				Map.of(
						AiCapability.REALTIME, List.of("realtime-primary", "realtime-backup"),
						AiCapability.SCORING, List.of("scoring-primary", AlternativeScoringProvider.MODEL_ID),
						AiCapability.TRANSCRIPTION, List.of("asr-primary", "asr-backup")));
		List<AiInvocationAttempt> attempts = new ArrayList<>();
		registry.configureDynamicRuntime(null, attempts::add, null, credentials());

		assertEquals("realtime-answer", registry.exchangeRealtimeSdp("offer", "token"));
		assertEquals("{\"totalScore\":99}", registry.evaluatePronunciation(
				"hello", new Byte[] {1}, "token"));
		assertEquals("backup-transcript", registry.convertAudioToText(
				new Byte[] {1}, "token"));
		assertEquals(List.of("FAILED", "SUCCEEDED", "FAILED", "SUCCEEDED", "FAILED", "SUCCEEDED"),
				attempts.stream().map(AiInvocationAttempt::status).toList());
		assertEquals(List.of(AiCapability.REALTIME, AiCapability.REALTIME,
				AiCapability.SCORING, AiCapability.SCORING,
				AiCapability.TRANSCRIPTION, AiCapability.TRANSCRIPTION),
				attempts.stream().map(AiInvocationAttempt::capability).toList());
	}

	@Test
	void failsOverTtsAndPreservesSuccessfulRoutedResultMetadata() {
		FailingTtsProvider primary = new FailingTtsProvider("tts-primary");
		SuccessfulTtsProvider fallback = new SuccessfulTtsProvider("tts-backup");
		AiProviderRegistry registry = new AiProviderRegistry(
				List.of(new StubRealtimeProvider()),
				llmProviders(),
				List.of(new StubScoringProvider()),
				List.of(primary, fallback),
				List.of(new StubTranscriptionProvider()),
				Map.of(AiCapability.TTS, List.of("tts-primary", "tts-backup")));
		List<AiInvocationAttempt> attempts = new ArrayList<>();
		registry.configureDynamicRuntime(null, attempts::add, null, credentials());

		AiProviderRegistry.RoutedResult<Byte[]> result = registry.generateSpeechAudioRouted(
				new AiInvocationContext(UUID.randomUUID(), "user", "session", "tts", "default"),
				"hello", "token");

		assertEquals("tts-backup", result.modelId());
		assertEquals("tts-backup", result.providerId());
		assertArrayEquals(new Byte[] {3, 4}, result.response());
		assertEquals("SUCCEEDED", attempts.getLast().status());
		assertEquals("tts-primary", attempts.getLast().fallbackFromModelId());
	}

	@Test
	void rethrowsFinalFailureForEachRoutedCapabilityAndAuditsIt() {
		FailingRealtimeProvider realtime = new FailingRealtimeProvider("realtime-only", ProviderType.QWEN);
		FailingScoringProvider scoring = new FailingScoringProvider("scoring-only");
		FailingTranscriptionProvider transcription = new FailingTranscriptionProvider("asr-only");
		FailingTtsProvider tts = new FailingTtsProvider("tts-only");
		AiProviderRegistry registry = new AiProviderRegistry(
				List.of(realtime), llmProviders(), List.of(scoring), List.of(tts), List.of(transcription),
				Map.of(
						AiCapability.REALTIME, List.of("realtime-only"),
						AiCapability.SCORING, List.of("scoring-only"),
						AiCapability.TRANSCRIPTION, List.of("asr-only"),
						AiCapability.TTS, List.of("tts-only")));
		List<AiInvocationAttempt> attempts = new ArrayList<>();
		registry.configureDynamicRuntime(null, attempts::add, null, credentials());

		assertEquals("REALTIME_DOWN", assertThrows(BusinessException.class,
				() -> registry.exchangeRealtimeSdp("offer", "token")).code());
		assertEquals("SCORING_DOWN", assertThrows(BusinessException.class,
				() -> registry.evaluatePronunciation("hello", new Byte[] {1}, "token")).code());
		assertEquals("ASR_DOWN", assertThrows(BusinessException.class,
				() -> registry.convertAudioToText(new Byte[] {1}, "token")).code());
		assertEquals("TTS_DOWN", assertThrows(BusinessException.class,
				() -> registry.generateSpeechAudioBytes("hello", "token")).code());
		assertEquals(List.of("FAILED", "FAILED", "FAILED", "FAILED"), attempts.stream()
				.map(AiInvocationAttempt::status).toList());
		assertTrue(attempts.stream().allMatch(AiInvocationAttempt::retryable));
	}

	@Test
	void rejectsCapabilityMismatchedProviderAndDuplicateModelsAcrossCapabilities() {
		@SuppressWarnings({"rawtypes", "unchecked"})
		List<RealtimeProvider> wrongProviders = (List) List.of(new StubScoringProvider());
		assertThrows(IllegalStateException.class, () -> new AiProviderRegistry(
				wrongProviders, llmProviders(), List.of(), List.of(), List.of()));

		assertThrows(IllegalStateException.class, () -> new AiProviderRegistry(
				List.of(),
				List.of(new ConfiguredModelLlmProvider("qwen", "shared-model")),
				List.of(),
				List.of(new SharedModelTtsProvider()),
				List.of()));
	}

	private static AiRuntimeConfiguration runtime(
			Map<String, AiProviderConfiguration> providers,
			Map<String, AiModelConfiguration> models) {
		return runtime(providers, models, Map.of("default", Map.of(AiCapability.LLM, List.of(
				AiProviderRegistry.QWEN_LLM_PLUS, AiProviderRegistry.DEEPSEEK_CHAT))));
	}

	private static AiRuntimeConfiguration runtime(
			Map<String, AiProviderConfiguration> providers,
			Map<String, AiModelConfiguration> models,
			Map<String, Map<AiCapability, List<String>>> routes) {
		return new AiRuntimeConfiguration(
				providers,
				models,
				routes,
				true);
	}

	private static AiProviderConfiguration provider(String providerId, String adapterType, boolean enabled) {
		return new AiProviderConfiguration(
				providerId, providerId, adapterType, null, enabled, 10_000, 60_000, 1);
	}

	private static AiModelConfiguration model(String modelId, String providerId) {
		return new AiModelConfiguration(
				modelId, providerId, modelId, AiCapability.LLM, true, "TOKENS",
				BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "CNY");
	}

	private static AiProviderCredentialStore credentials() {
		return new AiProviderCredentialStore() {
			@Override public String credentialOrFallback(String providerId, String fallback) { return fallback; }
			@Override public String credentialOrFallback(String providerId, String field, String fallback) { return fallback; }
			@Override public Map<String, String> credentialsOrFallback(String providerId, Map<String, String> fallback) { return fallback; }
			@Override public CredentialStatus status(String providerId) { return new CredentialStatus(false, null, false, List.of()); }
			@Override public CredentialStatus replace(String providerId, Map<String, String> values) { throw new UnsupportedOperationException(); }
		};
	}

	private AiProviderRegistry registry(RealtimeProvider realtimeProvider) {
		return new AiProviderRegistry(
				List.of(realtimeProvider),
				llmProviders(),
				List.of(new StubScoringProvider()),
				ttsProviders(),
				List.of(new StubTranscriptionProvider()));
	}

	private AiProviderRegistry realtimeRegistry(RealtimeProvider... providers) {
		return new AiProviderRegistry(
				List.of(providers),
				llmProviders(),
				List.of(new StubScoringProvider()),
				ttsProviders(),
				List.of(new StubTranscriptionProvider()));
	}

	private AiProviderRegistry registry(
			List<LlmProvider> providers,
			Map<AiCapability, List<String>> routeOverrides) {
		return new AiProviderRegistry(
				List.of(new StubRealtimeProvider()),
				providers,
				List.of(new StubScoringProvider()),
				ttsProviders(),
				List.of(new StubTranscriptionProvider()),
				routeOverrides);
	}

	private List<LlmProvider> llmProviders() {
		return List.of(
				new StubQiniuMaasLlmProvider(AiProviderRegistry.QINIU_MAAS_DEEPSEEK_FLASH),
				new StubQiniuMaasLlmProvider(AiProviderRegistry.QINIU_MAAS_QWEN_PLUS),
				new StubQwenLlmProvider(),
				new StubDeepSeekLlmProvider());
	}

	private List<TtsProvider> ttsProviders() {
		return List.of(
				new StubQwenTtsProvider(),
				new StubAliyunTtsProvider(),
				new StubMiniMaxTtsProvider());
	}

	private static final class StubRealtimeProvider extends RealtimeProvider {
		private StubRealtimeProvider() {
			super(
					ProviderType.QWEN,
					Set.of(AiProviderRegistry.QWEN_REALTIME_FLASH));
		}

		@Override
		public String exchangeRealtimeSdp(
				String modelId,
				String offerSdp,
				String token) {
			return "answer";
		}
	}

	private static final class StubQiniuRealtimeProvider extends RealtimeProvider {
		private StubQiniuRealtimeProvider() {
			super(ProviderType.QINIU, Set.of(AiProviderRegistry.QINIU_REALTIME_PLUS));
		}

		@Override
		public String exchangeRealtimeSdp(
				String modelId,
				String offerSdp,
				String token) {
			return "qiniu-answer";
		}
	}

	private static final class FailingRealtimeProvider extends RealtimeProvider {
		private FailingRealtimeProvider(String modelId, ProviderType providerType) {
			super(providerType, Set.of(modelId));
		}

		@Override
		public String exchangeRealtimeSdp(String modelId, String offerSdp, String token) {
			throw new BusinessException("REALTIME_DOWN", "realtime unavailable");
		}
	}

	private static final class SuccessfulRealtimeProvider extends RealtimeProvider {
		private SuccessfulRealtimeProvider(String modelId, ProviderType providerType) {
			super(providerType, Set.of(modelId));
		}

		@Override
		public String exchangeRealtimeSdp(String modelId, String offerSdp, String token) {
			return "realtime-answer";
		}
	}

	private static final class StubQwenLlmProvider extends LlmProvider {
		private int calls;
		private BusinessException failure;
		private String token;

		private StubQwenLlmProvider() {
			super("qwen", Set.of(AiProviderRegistry.QWEN_LLM_PLUS));
		}

		@Override
		public String executeLlmTask(String prompt, String token) {
			calls++;
			this.token = token;
			if (failure != null) {
				throw failure;
			}
			return "qwen";
		}
	}

	private static final class StubDeepSeekLlmProvider extends LlmProvider {
		private int calls;
		private BusinessException failure;

		private StubDeepSeekLlmProvider() {
			super("deepseek", Set.of(AiProviderRegistry.DEEPSEEK_CHAT));
		}

		@Override
		public String executeLlmTask(String prompt, String token) {
			calls++;
			if (failure != null) {
				throw failure;
			}
			return "deepseek";
		}
	}

	private static final class StubQiniuMaasLlmProvider extends LlmProvider {

		private StubQiniuMaasLlmProvider(String model) {
			super("qiniu-maas", Set.of(model));
		}

		@Override
		public String executeLlmTask(String prompt, String token) {
			return "qiniu-maas";
		}
	}

	private static final class FailingQiniuMaasLlmProvider extends LlmProvider {

		private FailingQiniuMaasLlmProvider(String model) {
			super("qiniu-maas", Set.of(model));
		}

		@Override
		public String executeLlmTask(String prompt, String token) {
			throw new BusinessException("QINIU_MAAS_LLM_IO_ERROR", "unavailable");
		}
	}

	private static final class ConfiguredModelLlmProvider extends LlmProvider {

		private ConfiguredModelLlmProvider(String providerId, String modelId) {
			super(providerId, Set.of(modelId));
		}

		@Override
		public String executeLlmTask(String prompt, String token) {
			return providerId();
		}
	}

	private static final class StubScoringProvider extends ScoringProvider {
		private int calls;

		private StubScoringProvider() {
			super(
					"iflytek",
					Set.of(AiProviderRegistry.IFLYTEK_PRONUNCIATION_SCORING));
		}

		@Override
		public String evaluatePronunciation(String text, byte[] audio, String token) {
			calls++;
			return "{}";
		}
	}

	private static final class AlternativeScoringProvider extends ScoringProvider {

		private static final String MODEL_ID = "alternative-pronunciation-scoring";
		private int calls;

		private AlternativeScoringProvider() {
			super("future-vendor", Set.of(MODEL_ID));
		}

		@Override
		public String evaluatePronunciation(String text, byte[] audio, String token) {
			calls++;
			return "{\"totalScore\":99}";
		}
	}

	private static final class FailingScoringProvider extends ScoringProvider {
		private FailingScoringProvider(String modelId) {
			super("scoring-provider-" + modelId, Set.of(modelId));
		}

		@Override
		public String evaluatePronunciation(String text, byte[] audio, String token) {
			throw new BusinessException("SCORING_DOWN", "scoring unavailable");
		}
	}

	private static final class StubAliyunTtsProvider extends TtsProvider {
		private StubAliyunTtsProvider() {
			super("aliyun", Set.of(AiProviderRegistry.ALIYUN_TTS));
		}

	}

	private static final class StubQwenTtsProvider extends TtsProvider {
		private int calls;

		private StubQwenTtsProvider() {
			super("qwen", Set.of(AiProviderRegistry.QWEN_TTS));
		}

		@Override
		public byte[] generateSpeechAudio(String text, String token) {
			calls++;
			return new byte[] {1, 2};
		}
	}

	private static final class StubMiniMaxTtsProvider extends TtsProvider {
		private StubMiniMaxTtsProvider() {
			super("minimax", Set.of(AiProviderRegistry.MINIMAX_TTS));
		}

	}

	private static final class MeteredFailingQwenTtsProvider extends TtsProvider {
		private MeteredFailingQwenTtsProvider() {
			super("qwen", Set.of(AiProviderRegistry.QWEN_TTS));
		}

		@Override
		public AiProviderResponse<byte[]> generateSpeechAudioMeasured(String text, String token) {
			throw new MeteredProviderException(
					"QWEN_TTS_AUDIO_DOWNLOAD_FAILED",
					"download failed",
					true,
					"qwen-billed-request",
					ProviderUsage.ttsInput(text));
		}
	}

	private static final class SuccessfulAliyunTtsProvider extends TtsProvider {
		private SuccessfulAliyunTtsProvider() {
			super("aliyun", Set.of(AiProviderRegistry.ALIYUN_TTS));
		}

		@Override
		public AiProviderResponse<byte[]> generateSpeechAudioMeasured(String text, String token) {
			return new AiProviderResponse<>(
					new byte[] {1},
					"aliyun-success-request",
					ProviderUsage.tts(text, new byte[] {1}));
		}
	}

	private static final class FailingTtsProvider extends TtsProvider {
		private FailingTtsProvider(String modelId) {
			super(modelId, Set.of(modelId));
		}

		@Override
		public byte[] generateSpeechAudio(String text, String token) {
			throw new BusinessException("TTS_DOWN", "tts unavailable");
		}
	}

	private static final class SuccessfulTtsProvider extends TtsProvider {
		private SuccessfulTtsProvider(String modelId) {
			super(modelId, Set.of(modelId));
		}

		@Override
		public AiProviderResponse<byte[]> generateSpeechAudioMeasured(String text, String token) {
			return new AiProviderResponse<>(
					new byte[] {3, 4}, "tts-success-request", ProviderUsage.tts(text, new byte[] {3, 4}));
		}
	}

	private static final class StubTranscriptionProvider extends TranscriptionProvider {

		private static final String MODEL_ID = "stub-asr";
		private int calls;

		private StubTranscriptionProvider() {
			super("qwen", Set.of(MODEL_ID));
		}

		@Override
		public String convertAudioToText(byte[] audio, String token) {
			calls++;
			return "transcript";
		}
	}

	private static final class FailingTranscriptionProvider extends TranscriptionProvider {
		private FailingTranscriptionProvider(String modelId) {
			super(modelId, Set.of(modelId));
		}

		@Override
		public String convertAudioToText(byte[] audio, String token) {
			throw new BusinessException("ASR_DOWN", "transcription unavailable");
		}
	}

	private static final class AlternativeTranscriptionProvider extends TranscriptionProvider {
		private AlternativeTranscriptionProvider(String modelId) {
			super(modelId, Set.of(modelId));
		}

		@Override
		public String convertAudioToText(byte[] audio, String token) {
			return "backup-transcript";
		}
	}

	private static final class SharedModelTtsProvider extends TtsProvider {
		private SharedModelTtsProvider() {
			super("shared", Set.of("shared-model"));
		}
	}
}
