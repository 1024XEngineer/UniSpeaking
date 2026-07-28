package com.unispeaking.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.domain.dto.ai.LlmTaskRequest;
import com.unispeaking.domain.dto.ai.LlmTaskResponse;
import com.unispeaking.domain.dto.ai.PronunciationEvaluationRequest;
import com.unispeaking.domain.dto.ai.PronunciationEvaluationResponse;
import com.unispeaking.domain.dto.ai.RealtimeSdpExchangeRequest;
import com.unispeaking.domain.dto.ai.RealtimeSdpExchangeResponse;
import com.unispeaking.domain.dto.ai.SpeechAudioRequest;
import com.unispeaking.domain.dto.ai.SpeechAudioResponse;
import com.unispeaking.domain.vo.ai.AiCapability;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.exception.BusinessException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiProviderRegistryTest {

	@Test
	void selectsProvidersByCapabilityAndModel() {
		StubRealtimeProvider realtime = new StubRealtimeProvider();
		AiProviderRegistry registry = registry(realtime);

		assertEquals(
				AiProviderRegistry.QWEN_REALTIME_FLASH,
				registry.defaultModel(AiCapability.REALTIME));
		assertEquals(AiProviderRegistry.QWEN_LLM_PLUS, registry.defaultModel(AiCapability.LLM));
		assertSame(
				realtime,
				registry.getRealtimeProvider(AiProviderRegistry.QWEN_REALTIME_PLUS));
		assertEquals(
				"answer",
				registry.exchangeRealtimeSdp(
						AiProviderRegistry.QWEN_REALTIME_FLASH,
						new RealtimeSdpExchangeRequest(null, AiProviderRegistry.QWEN_REALTIME_FLASH, "offer", "key"))
						.answerSdp());
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

	private AiProviderRegistry registry(RealtimeProvider realtimeProvider) {
		return new AiProviderRegistry(
				List.of(realtimeProvider),
				llmProviders(),
				List.of(new StubScoringProvider()),
				ttsProviders(),
				List.of());
	}

	private List<LlmProvider> llmProviders() {
		return List.of(new StubQwenLlmProvider(), new StubDeepSeekLlmProvider());
	}

	private List<TtsProvider> ttsProviders() {
		return List.of(new StubAliyunTtsProvider(), new StubMiniMaxTtsProvider());
	}

	private static final class StubRealtimeProvider extends RealtimeProvider {
		private StubRealtimeProvider() {
			super(
					ProviderType.QWEN,
					Set.of(
							AiProviderRegistry.QWEN_REALTIME_FLASH,
							AiProviderRegistry.QWEN_REALTIME_PLUS));
		}

		@Override
		public RealtimeSdpExchangeResponse exchangeRealtimeSdp(RealtimeSdpExchangeRequest request) {
			return new RealtimeSdpExchangeResponse("answer", null);
		}
	}

	private static final class StubQwenLlmProvider extends LlmProvider {
		private StubQwenLlmProvider() {
			super(ProviderType.QWEN, Set.of(AiProviderRegistry.QWEN_LLM_PLUS));
		}

		@Override
		public LlmTaskResponse executeLlmTask(LlmTaskRequest request) {
			return null;
		}
	}

	private static final class StubDeepSeekLlmProvider extends LlmProvider {
		private StubDeepSeekLlmProvider() {
			super(ProviderType.DEEPSEEK, Set.of(AiProviderRegistry.DEEPSEEK_CHAT));
		}

		@Override
		public LlmTaskResponse executeLlmTask(LlmTaskRequest request) {
			return null;
		}
	}

	private static final class StubScoringProvider extends ScoringProvider {
		private StubScoringProvider() {
			super(
					ProviderType.IFLYTEK,
					Set.of(AiProviderRegistry.IFLYTEK_PRONUNCIATION_SCORING));
		}

		@Override
		public PronunciationEvaluationResponse evaluatePronunciation(
				PronunciationEvaluationRequest request) {
			return null;
		}
	}

	private static final class StubAliyunTtsProvider extends TtsProvider {
		private StubAliyunTtsProvider() {
			super(ProviderType.ALIYUN, Set.of(AiProviderRegistry.ALIYUN_TTS));
		}

		@Override
		public SpeechAudioResponse generateSpeechAudio(SpeechAudioRequest request) {
			return null;
		}
	}

	private static final class StubMiniMaxTtsProvider extends TtsProvider {
		private StubMiniMaxTtsProvider() {
			super(ProviderType.MINIMAX, Set.of(AiProviderRegistry.MINIMAX_TTS));
		}

		@Override
		public SpeechAudioResponse generateSpeechAudio(SpeechAudioRequest request) {
			return null;
		}
	}
}
