package com.unispeaking.provider;

import com.unispeaking.domain.dto.ai.AudioTranscriptionRequest;
import com.unispeaking.domain.dto.ai.AudioTranscriptionResponse;
import com.unispeaking.domain.dto.ai.LlmTaskRequest;
import com.unispeaking.domain.dto.ai.LlmTaskResponse;
import com.unispeaking.domain.dto.ai.PronunciationEvaluationRequest;
import com.unispeaking.domain.dto.ai.PronunciationEvaluationResponse;
import com.unispeaking.domain.dto.ai.RealtimeSdpExchangeRequest;
import com.unispeaking.domain.dto.ai.RealtimeSdpExchangeResponse;
import com.unispeaking.domain.dto.ai.SpeechAudioRequest;
import com.unispeaking.domain.dto.ai.SpeechAudioResponse;
import com.unispeaking.domain.vo.ai.AiCapability;
import com.unispeaking.domain.vo.ai.AiModelDefinition;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.exception.BusinessException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiProviderRegistry {

	public static final String QWEN_REALTIME_FLASH = "qwen3.5-omni-flash-realtime";
	public static final String QWEN_REALTIME_PLUS = "qwen3.5-omni-plus-realtime";
	public static final String QWEN_LLM_PLUS = "qwen3.5-plus";
	public static final String DEEPSEEK_CHAT = "deepseek-chat";
	public static final String IFLYTEK_PRONUNCIATION_SCORING = "iflytek-pronunciation-evaluation";
	public static final String ALIYUN_TTS = "aliyun-tts";
	public static final String MINIMAX_TTS = "minimax-tts";

	private static final List<AiModelDefinition> MODEL_CATALOG = List.of(
			new AiModelDefinition(QWEN_REALTIME_FLASH, ProviderType.QWEN, AiCapability.REALTIME, true),
			new AiModelDefinition(QWEN_REALTIME_PLUS, ProviderType.QWEN, AiCapability.REALTIME, false),
			new AiModelDefinition(QWEN_LLM_PLUS, ProviderType.QWEN, AiCapability.LLM, true),
			new AiModelDefinition(DEEPSEEK_CHAT, ProviderType.DEEPSEEK, AiCapability.LLM, false),
			new AiModelDefinition(
					IFLYTEK_PRONUNCIATION_SCORING,
					ProviderType.IFLYTEK,
					AiCapability.SCORING,
					true),
			new AiModelDefinition(ALIYUN_TTS, ProviderType.ALIYUN, AiCapability.TTS, true),
			new AiModelDefinition(MINIMAX_TTS, ProviderType.MINIMAX, AiCapability.TTS, false));

	private final Map<String, AiModelDefinition> modelDefinitions;
	private final Map<AiCapability, String> defaultModels;
	private final Map<String, RealtimeProvider> realtimeProviders;
	private final Map<String, LlmProvider> llmProviders;
	private final Map<String, ScoringProvider> scoringProviders;
	private final Map<String, TtsProvider> ttsProviders;
	private final Map<String, TranscriptionProvider> transcriptionProviders;

	public AiProviderRegistry(
			List<RealtimeProvider> realtimeProviders,
			List<LlmProvider> llmProviders,
			List<ScoringProvider> scoringProviders,
			List<TtsProvider> ttsProviders,
			List<TranscriptionProvider> transcriptionProviders) {
		this.modelDefinitions = buildModelDefinitions();
		this.defaultModels = buildDefaultModels();
		this.realtimeProviders = registerProviders(realtimeProviders, AiCapability.REALTIME);
		this.llmProviders = registerProviders(llmProviders, AiCapability.LLM);
		this.scoringProviders = registerProviders(scoringProviders, AiCapability.SCORING);
		this.ttsProviders = registerProviders(ttsProviders, AiCapability.TTS);
		this.transcriptionProviders = registerProviders(
				transcriptionProviders,
				AiCapability.TRANSCRIPTION);
		validateCatalogRegistrations();
	}

	public List<AiModelDefinition> models() {
		return MODEL_CATALOG;
	}

	public AiModelDefinition getModel(String modelId) {
		AiModelDefinition definition = modelDefinitions.get(AbstractAiProvider.normalizeModelId(modelId));
		if (definition == null) {
			throw new BusinessException("AI_MODEL_NOT_FOUND", "AI model is not registered: " + modelId);
		}
		return definition;
	}

	public String defaultModel(AiCapability capability) {
		String modelId = defaultModels.get(capability);
		if (modelId == null) {
			throw new BusinessException(
					"AI_DEFAULT_MODEL_NOT_FOUND",
					"No default AI model is registered for " + capability);
		}
		return modelId;
	}

	public RealtimeProvider getRealtimeProvider(String modelId) {
		return requiredProvider(realtimeProviders, modelId, AiCapability.REALTIME);
	}

	public LlmProvider getLlmProvider(String modelId) {
		return requiredProvider(llmProviders, modelId, AiCapability.LLM);
	}

	public ScoringProvider getScoringProvider(String modelId) {
		return requiredProvider(scoringProviders, modelId, AiCapability.SCORING);
	}

	public TtsProvider getTtsProvider(String modelId) {
		return requiredProvider(ttsProviders, modelId, AiCapability.TTS);
	}

	public TranscriptionProvider getTranscriptionProvider(String modelId) {
		return requiredProvider(transcriptionProviders, modelId, AiCapability.TRANSCRIPTION);
	}

	public RealtimeSdpExchangeResponse exchangeRealtimeSdp(
			String modelId,
			RealtimeSdpExchangeRequest request) {
		if (request == null) {
			throw new BusinessException(
					"INVALID_SDP_REQUEST",
					"Realtime SDP exchange request is required");
		}
		String normalizedModelId = AbstractAiProvider.normalizeModelId(modelId);
		if (request.model() != null
				&& !request.model().isBlank()
				&& !normalizedModelId.equals(AbstractAiProvider.normalizeModelId(request.model()))) {
			throw new BusinessException(
					"AI_MODEL_REQUEST_MISMATCH",
					"Registry model " + modelId + " does not match request model " + request.model());
		}
		RealtimeSdpExchangeRequest routedRequest = new RealtimeSdpExchangeRequest(
				request.context(),
				getModel(modelId).modelId(),
				request.offerSdp(),
				request.apiKey());
		return getRealtimeProvider(modelId).exchangeRealtimeSdp(routedRequest);
	}

	public SpeechAudioResponse generateSpeechAudio(String modelId, SpeechAudioRequest request) {
		return getTtsProvider(modelId).generateSpeechAudio(request);
	}

	public LlmTaskResponse executeLlmTask(String modelId, LlmTaskRequest request) {
		return getLlmProvider(modelId).executeLlmTask(request);
	}

	public AudioTranscriptionResponse convertAudioToText(
			String modelId,
			AudioTranscriptionRequest request) {
		return getTranscriptionProvider(modelId).convertAudioToText(request);
	}

	public PronunciationEvaluationResponse evaluatePronunciation(
			String modelId,
			PronunciationEvaluationRequest request) {
		return getScoringProvider(modelId).evaluatePronunciation(request);
	}

	private Map<String, AiModelDefinition> buildModelDefinitions() {
		Map<String, AiModelDefinition> definitions = new LinkedHashMap<>();
		for (AiModelDefinition definition : MODEL_CATALOG) {
			String modelId = AbstractAiProvider.normalizeModelId(definition.modelId());
			if (definitions.putIfAbsent(modelId, definition) != null) {
				throw new IllegalStateException("Duplicate AI model definition: " + modelId);
			}
		}
		return Map.copyOf(definitions);
	}

	private Map<AiCapability, String> buildDefaultModels() {
		Map<AiCapability, String> defaults = new EnumMap<>(AiCapability.class);
		for (AiModelDefinition definition : MODEL_CATALOG) {
			if (definition.defaultModel()
					&& defaults.putIfAbsent(definition.capability(), definition.modelId()) != null) {
				throw new IllegalStateException(
						"Duplicate default AI model for " + definition.capability());
			}
		}
		return Map.copyOf(defaults);
	}

	private <T extends AiProvider> Map<String, T> registerProviders(
			List<T> providers,
			AiCapability capability) {
		Map<String, T> registered = new LinkedHashMap<>();
		for (T provider : providers) {
			if (provider.capability() != capability) {
				throw new IllegalStateException(
						"Provider capability mismatch: " + provider.getClass().getName());
			}
			for (String modelId : provider.supportedModels()) {
				AiModelDefinition definition = modelDefinitions.get(modelId);
				if (definition == null) {
					throw new IllegalStateException(
							"Provider references an unregistered AI model: " + modelId);
				}
				if (definition.capability() != capability || definition.providerType() != provider.type()) {
					throw new IllegalStateException(
							"Provider does not match AI model definition: " + modelId);
				}
				if (registered.putIfAbsent(modelId, provider) != null) {
					throw new IllegalStateException(
							"Duplicate AI provider registration for model " + modelId);
				}
			}
		}
		return Map.copyOf(registered);
	}

	private <T extends AiProvider> T requiredProvider(
			Map<String, T> providers,
			String modelId,
			AiCapability expectedCapability) {
		AiModelDefinition definition = getModel(modelId);
		if (definition.capability() != expectedCapability) {
			throw new BusinessException(
					"AI_MODEL_CAPABILITY_MISMATCH",
					modelId + " is a " + definition.capability() + " model, not " + expectedCapability);
		}
		T provider = providers.get(AbstractAiProvider.normalizeModelId(modelId));
		if (provider == null) {
			throw new BusinessException(
					"AI_PROVIDER_NOT_FOUND",
					"No " + expectedCapability + " provider is registered for " + modelId);
		}
		return provider;
	}

	private void validateCatalogRegistrations() {
		for (AiModelDefinition definition : MODEL_CATALOG) {
			Map<String, ? extends AiProvider> providers = switch (definition.capability()) {
				case REALTIME -> realtimeProviders;
				case LLM -> llmProviders;
				case SCORING -> scoringProviders;
				case TTS -> ttsProviders;
				case TRANSCRIPTION -> transcriptionProviders;
			};
			if (!providers.containsKey(AbstractAiProvider.normalizeModelId(definition.modelId()))) {
				throw new IllegalStateException(
						"No provider implementation registered for AI model " + definition.modelId());
			}
		}
	}
}
