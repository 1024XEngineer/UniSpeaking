package com.unispeaking.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.provider.AiModelDefinition;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
		assertEquals(AiProviderRegistry.QWEN_LLM_PLUS, registry.defaultModel(AiCapability.LLM));
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
	void usesQiniuAsThePrimaryRealtimeModelAndQwenForOtherGenerativeCapabilities() {
		AiProviderRegistry registry = realtimeRegistry(
				new StubQiniuRealtimeProvider(), new StubRealtimeProvider());

		assertEquals(
				AiProviderRegistry.QINIU_REALTIME_PLUS,
				registry.defaultModel(AiCapability.REALTIME));
		assertEquals(
				AiProviderRegistry.QWEN_LLM_PLUS,
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
		return List.of(new StubQwenLlmProvider(), new StubDeepSeekLlmProvider());
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
