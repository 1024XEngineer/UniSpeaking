package com.unispeaking.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	private static AiRuntimeConfiguration runtime(
			Map<String, AiProviderConfiguration> providers,
			Map<String, AiModelConfiguration> models) {
		return new AiRuntimeConfiguration(
				providers,
				models,
				Map.of("default", Map.of(AiCapability.LLM, List.of(
						AiProviderRegistry.QWEN_LLM_PLUS, AiProviderRegistry.DEEPSEEK_CHAT))),
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
			@Override public CredentialStatus status(String providerId) { return new CredentialStatus(false, null, false); }
			@Override public CredentialStatus replace(String providerId, String plaintext) { throw new UnsupportedOperationException(); }
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

	private static final class StubQwenLlmProvider extends LlmProvider {
		private int calls;
		private BusinessException failure;

		private StubQwenLlmProvider() {
			super("qwen", Set.of(AiProviderRegistry.QWEN_LLM_PLUS));
		}

		@Override
		public String executeLlmTask(String prompt, String token) {
			calls++;
			if (failure != null) {
				throw failure;
			}
			return "qwen";
		}
	}

	private static final class StubDeepSeekLlmProvider extends LlmProvider {
		private int calls;

		private StubDeepSeekLlmProvider() {
			super("deepseek", Set.of(AiProviderRegistry.DEEPSEEK_CHAT));
		}

		@Override
		public String executeLlmTask(String prompt, String token) {
			calls++;
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

	private static final class StubAliyunTtsProvider extends TtsProvider {
		private StubAliyunTtsProvider() {
			super("aliyun", Set.of(AiProviderRegistry.ALIYUN_TTS));
		}

	}

	private static final class StubQwenTtsProvider extends TtsProvider {
		private StubQwenTtsProvider() {
			super("qwen", Set.of(AiProviderRegistry.QWEN_TTS));
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

	private static final class StubTranscriptionProvider extends TranscriptionProvider {

		private static final String MODEL_ID = "stub-asr";

		private StubTranscriptionProvider() {
			super("qwen", Set.of(MODEL_ID));
		}

		@Override
		public String convertAudioToText(byte[] audio, String token) {
			return "transcript";
		}
	}
}
