package com.unispeaking.provider;

import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.provider.AiModelDefinition;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.common.exception.BusinessException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiProviderRegistry {

	private static final Logger LOGGER = LoggerFactory.getLogger(AiProviderRegistry.class);

	/**
	 * Internal routing result for audit, metering, and diagnostics. User-facing
	 * business methods can continue returning only the response payload.
	 */
	public record RoutedResult<T>(
			String modelId,
			String providerId,
			AiCapability capability,
			T response) {
	}

	public static final String QWEN_REALTIME_FLASH = "qwen3.5-omni-flash-realtime";
	public static final String QINIU_REALTIME_PLUS = "qwen3.5-omni-plus-realtime";
	public static final String QWEN_LLM_PLUS = "qwen3.5-plus";
	public static final String DEEPSEEK_CHAT = "deepseek-v4-flash";
	public static final String QINIU_MAAS_DEEPSEEK_FLASH = "deepseek/deepseek-v4-flash";
	public static final String QINIU_MAAS_QWEN_PLUS = "qwen/qwen3.5-plus";
	public static final String QWEN_ASR = "qwen3-asr-flash";
	public static final String DOUBAO_ASR = "volc.bigasr.auc_turbo";
	public static final String IFLYTEK_PRONUNCIATION_SCORING = "iflytek-suntone";
	public static final String QWEN_TTS = "qwen3-tts-flash";
	public static final String ALIYUN_TTS = "cosyvoice-v3-flash";
	public static final String MINIMAX_TTS = "speech-2.8-hd";

	private static final Map<AiCapability, List<String>> DEFAULT_MODEL_ROUTES = Map.of(
			AiCapability.REALTIME, List.of(QINIU_REALTIME_PLUS, QWEN_REALTIME_FLASH),
			AiCapability.LLM, List.of(QINIU_MAAS_QWEN_PLUS, QWEN_LLM_PLUS),
			AiCapability.SCORING, List.of(IFLYTEK_PRONUNCIATION_SCORING),
			AiCapability.TTS, List.of(QWEN_TTS, ALIYUN_TTS, MINIMAX_TTS),
			AiCapability.TRANSCRIPTION, List.of(QWEN_ASR, DOUBAO_ASR));

	private static final Map<AiCapability, List<String>> DEFAULT_PROVIDER_ROUTES = Map.of(
			AiCapability.REALTIME, List.of("qiniu", "qwen"),
			AiCapability.LLM, List.of("qiniu-maas", "qwen"),
			AiCapability.SCORING, List.of("iflytek"),
			AiCapability.TTS, List.of("qwen", "aliyun", "minimax"),
			AiCapability.TRANSCRIPTION, List.of("qwen", "doubao"));

	private final List<AiModelDefinition> models;
	private final Map<String, AiModelDefinition> modelDefinitions;
	private final Map<AiCapability, List<String>> modelRoutes;
	private final Map<String, RealtimeProvider> realtimeProviders;
	private final Map<String, LlmProvider> llmProviders;
	private final Map<String, ScoringProvider> scoringProviders;
	private final Map<String, TtsProvider> ttsProviders;
	private final Map<String, TranscriptionProvider> transcriptionProviders;

	@Autowired
	public AiProviderRegistry(
			List<RealtimeProvider> realtimeProviders,
			List<LlmProvider> llmProviders,
			List<ScoringProvider> scoringProviders,
			List<TtsProvider> ttsProviders,
			List<TranscriptionProvider> transcriptionProviders,
			@Value("${AI_PROVIDER_ROUTE_REALTIME:}")
			String realtimeRoute,
			@Value("${AI_PROVIDER_ROUTE_LLM:}")
			String llmRoute,
			@Value("${AI_PROVIDER_ROUTE_SCORING:}")
			String scoringRoute,
			@Value("${AI_PROVIDER_ROUTE_TTS:}")
			String ttsRoute,
			@Value("${AI_PROVIDER_ROUTE_TRANSCRIPTION:}")
			String transcriptionRoute) {
		this(
				realtimeProviders,
				llmProviders,
				scoringProviders,
				ttsProviders,
				transcriptionProviders,
				Map.of(
						AiCapability.REALTIME, parseRoute(realtimeRoute),
						AiCapability.LLM, parseRoute(llmRoute),
						AiCapability.SCORING, parseRoute(scoringRoute),
						AiCapability.TTS, parseRoute(ttsRoute),
						AiCapability.TRANSCRIPTION, parseRoute(transcriptionRoute)));
	}

	public AiProviderRegistry(
			List<RealtimeProvider> realtimeProviders,
			List<LlmProvider> llmProviders,
			List<ScoringProvider> scoringProviders,
			List<TtsProvider> ttsProviders,
			List<TranscriptionProvider> transcriptionProviders) {
		this(
				realtimeProviders,
				llmProviders,
				scoringProviders,
				ttsProviders,
				transcriptionProviders,
				Map.of());
	}

	public AiProviderRegistry(
			List<RealtimeProvider> realtimeProviders,
			List<LlmProvider> llmProviders,
			List<ScoringProvider> scoringProviders,
			List<TtsProvider> ttsProviders,
			List<TranscriptionProvider> transcriptionProviders,
			Map<AiCapability, List<String>> configuredRoutes) {
		this.realtimeProviders = registerProviders(realtimeProviders, AiCapability.REALTIME);
		this.llmProviders = registerProviders(llmProviders, AiCapability.LLM);
		this.scoringProviders = registerProviders(scoringProviders, AiCapability.SCORING);
		this.ttsProviders = registerProviders(ttsProviders, AiCapability.TTS);
		this.transcriptionProviders = registerProviders(
				transcriptionProviders,
				AiCapability.TRANSCRIPTION);
		this.modelRoutes = buildModelRoutes(configuredRoutes);
		this.modelDefinitions = buildModelDefinitions();
		this.models = List.copyOf(modelDefinitions.values());
	}

	public List<AiModelDefinition> models() {
		return models;
	}

	public AiModelDefinition getModel(String modelId) {
		AiModelDefinition definition = modelDefinitions.get(AbstractAiProvider.normalizeModelId(modelId));
		if (definition == null) {
			throw new BusinessException("AI_MODEL_NOT_FOUND", "AI model is not registered: " + modelId);
		}
		return definition;
	}

	public String defaultModel(AiCapability capability) {
		List<String> route = modelRoutes.get(capability);
		if (route == null || route.isEmpty()) {
			throw new BusinessException(
					"AI_DEFAULT_MODEL_NOT_FOUND",
					"No default AI model is registered for " + capability);
		}
		return route.getFirst();
	}

	public List<String> route(AiCapability capability) {
		List<String> route = modelRoutes.get(capability);
		if (route == null || route.isEmpty()) {
			throw new BusinessException(
					"AI_PROVIDER_ROUTE_NOT_FOUND",
					"No AI provider route is registered for " + capability);
		}
		return route;
	}

	public RealtimeProvider getRealtimeProvider(String modelId) {
		return requiredProvider(realtimeProviders, modelId, AiCapability.REALTIME);
	}

	/**
	 * Routes Realtime SDP exchange without exposing the selected adapter to the
	 * user. When a model is explicit, its provider must match the provider hint.
	 * When no model is explicit, the Registry route is authoritative so changing
	 * the configured route transparently replaces the Realtime model.
	 */
	public <T> T routeRealtime(
			ProviderType requestedProvider,
			String requestedModel,
			BiFunction<String, RealtimeProvider, T> operation) {
		String model = AbstractAiProvider.normalizeModelId(requestedModel);
		List<String> models = model.isBlank()
				? route(AiCapability.REALTIME)
				: List.of(model);
		if (!model.isBlank()) {
			RealtimeProvider provider = getRealtimeProvider(model);
			if (requestedProvider != null && requestedProvider != provider.type()) {
				throw new BusinessException(
						"AI_PROVIDER_MODEL_MISMATCH",
						"Requested provider " + requestedProvider
								+ " does not own realtime model " + model);
			}
		}
		return invokeModels(
				AiCapability.REALTIME,
				models,
				modelId -> operation.apply(modelId, getRealtimeProvider(modelId)));
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

	public String exchangeRealtimeSdp(String modelId, String offerSdp, String token) {
		String registeredModelId = getModel(modelId).modelId();
		return getRealtimeProvider(registeredModelId)
				.exchangeRealtimeSdp(registeredModelId, offerSdp, token);
	}

	public String exchangeRealtimeSdp(String offerSdp, String token) {
		return routeRealtime(
				null,
				null,
				(modelId, provider) -> provider.exchangeRealtimeSdp(
						modelId,
						offerSdp,
						token));
	}

	public Byte[] generateSpeechAudio(String modelId, String text, String token) {
		return boxAudio(getTtsProvider(modelId).generateSpeechAudio(text, token));
	}

	public Byte[] generateSpeechAudio(String text, String token) {
		return generateSpeechAudioRouted(text, token).response();
	}

	public RoutedResult<Byte[]> generateSpeechAudioRouted(String text, String token) {
		return invokeRouteWithResult(
				AiCapability.TTS,
				modelId -> generateSpeechAudio(modelId, text, token));
	}

	public byte[] generateSpeechAudioBytes(String modelId, String text, String token) {
		return getTtsProvider(modelId).generateSpeechAudio(text, token);
	}

	public byte[] generateSpeechAudioBytes(String text, String token) {
		return unboxAudio(generateSpeechAudio(text, token));
	}

	public String executeLlmTask(String modelId, String prompt, String token) {
		return getLlmProvider(modelId).executeLlmTask(prompt, token);
	}

	public String executeLlmTask(String prompt, String token) {
		return executeLlmTaskRouted(prompt, token).response();
	}

	public RoutedResult<String> executeLlmTaskRouted(String prompt, String token) {
		return invokeRouteWithResult(
				AiCapability.LLM,
				modelId -> executeLlmTask(modelId, prompt, token));
	}

	public String convertAudioToText(String modelId, Byte[] audio, String token) {
		return getTranscriptionProvider(modelId).convertAudioToText(
				unboxAudio(audio),
				token);
	}

	public String convertAudioToText(Byte[] audio, String token) {
		return convertAudioToTextRouted(audio, token).response();
	}

	public RoutedResult<String> convertAudioToTextRouted(Byte[] audio, String token) {
		return invokeRouteWithResult(
				AiCapability.TRANSCRIPTION,
				modelId -> convertAudioToText(modelId, audio, token));
	}

	public String evaluatePronunciation(
			String modelId,
			String text,
			Byte[] audio,
			String token) {
		return getScoringProvider(modelId).evaluatePronunciation(
				text,
				unboxAudio(audio),
				token);
	}

	public String evaluatePronunciation(String text, Byte[] audio, String token) {
		return evaluatePronunciationRouted(text, audio, token).response();
	}

	public RoutedResult<String> evaluatePronunciationRouted(
			String text,
			Byte[] audio,
			String token) {
		return invokeRouteWithResult(
				AiCapability.SCORING,
				modelId -> evaluatePronunciation(modelId, text, audio, token));
	}

	private Map<String, AiModelDefinition> buildModelDefinitions() {
		Map<String, AiModelDefinition> definitions = new LinkedHashMap<>();
		for (AiCapability capability : AiCapability.values()) {
			Map<String, ? extends AbstractAiProvider> providers = providers(capability);
			if (providers.isEmpty()) {
				continue;
			}
			for (String modelId : orderedModelIds(capability, providers.keySet())) {
				AbstractAiProvider provider = providers.get(modelId);
				AiModelDefinition definition = new AiModelDefinition(
						modelId,
						provider.providerId(),
						capability,
						modelId.equals(defaultModel(capability)));
				if (definitions.putIfAbsent(modelId, definition) != null) {
					throw new IllegalStateException("Duplicate AI model definition: " + modelId);
				}
			}
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
	}

	private Map<AiCapability, List<String>> buildModelRoutes(
			Map<AiCapability, List<String>> configuredRoutes) {
		Map<AiCapability, List<String>> routes = new EnumMap<>(AiCapability.class);
		Map<AiCapability, List<String>> safeRoutes = configuredRoutes == null
				? Map.of()
				: configuredRoutes;
		for (AiCapability capability : AiCapability.values()) {
			Map<String, ? extends AbstractAiProvider> registered = providers(capability);
			if (registered.isEmpty()) {
				continue;
			}
			List<String> configured = safeRoutes.get(capability);
			List<String> route = configured == null || configured.isEmpty()
					? defaultRoute(capability, registered)
					: normalizeRoute(configured);
			for (String modelId : route) {
				if (!registered.containsKey(modelId)) {
					throw new IllegalStateException(
							"AI provider route references an unavailable "
									+ capability + " model: " + modelId);
				}
			}
			routes.put(capability, route);
		}
		return Map.copyOf(routes);
	}

	private List<String> defaultRoute(
			AiCapability capability,
			Map<String, ? extends AbstractAiProvider> registeredProviders) {
		LinkedHashSet<String> route = new LinkedHashSet<>();
		for (String modelId : DEFAULT_MODEL_ROUTES.getOrDefault(capability, List.of())) {
			if (registeredProviders.containsKey(modelId)) {
				route.add(modelId);
			}
		}
		for (String providerId : DEFAULT_PROVIDER_ROUTES.getOrDefault(capability, List.of())) {
			addProviderModelsIfAbsent(route, registeredProviders, providerId);
		}
		// The LLM default route is intentionally limited to Qiniu MaaS Qwen and
		// Alibaba Qwen. Do not append unrelated legacy providers after that route
		// has been established; explicit configured routes still remain untouched.
		if (capability == AiCapability.LLM
				&& route.contains(QINIU_MAAS_QWEN_PLUS)) {
			return List.copyOf(route);
		}
		for (AbstractAiProvider provider : registeredProviders.values()) {
			addProviderModelsIfAbsent(route, registeredProviders, provider.providerId());
		}
		return List.copyOf(route);
	}

	private void addProviderModelsIfAbsent(
			LinkedHashSet<String> route,
			Map<String, ? extends AbstractAiProvider> registeredProviders,
			String providerId) {
		boolean alreadyRouted = route.stream()
				.map(registeredProviders::get)
				.anyMatch(provider -> provider != null
						&& provider.providerId().equals(providerId));
		if (alreadyRouted) {
			return;
		}
		registeredProviders.forEach((modelId, provider) -> {
			if (provider.providerId().equals(providerId)) {
				route.add(modelId);
			}
		});
	}

	private <T extends AbstractAiProvider> Map<String, T> registerProviders(
			List<T> providers,
			AiCapability capability) {
		Map<String, T> registered = new LinkedHashMap<>();
		for (T provider : providers == null ? List.<T>of() : providers) {
			if (provider.capability() != capability) {
				throw new IllegalStateException(
						"Provider capability mismatch: " + provider.getClass().getName());
			}
			for (String modelId : provider.supportedModels()) {
				if (registered.putIfAbsent(modelId, provider) != null) {
					throw new IllegalStateException(
							"Duplicate AI provider registration for model " + modelId);
				}
			}
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(registered));
	}

	private <T> RoutedResult<T> invokeRouteWithResult(
			AiCapability capability,
			Function<String, T> operation) {
		return invokeModels(
				capability,
				route(capability),
				modelId -> {
					AiModelDefinition definition = getModel(modelId);
					return new RoutedResult<>(
							definition.modelId(),
							definition.providerId(),
							capability,
							operation.apply(modelId));
				});
	}

	private <T> T invokeModels(
			AiCapability capability,
			List<String> models,
			Function<String, T> operation) {
		BusinessException lastFailure = null;
		for (int index = 0; index < models.size(); index++) {
			String modelId = models.get(index);
			AiModelDefinition definition = getModel(modelId);
			long startedAt = System.nanoTime();
			LOGGER.info(
					"AI provider attempt capability={} model={} provider={} attempt={}/{}",
					capability,
					definition.modelId(),
					definition.providerId(),
					index + 1,
					models.size());
			try {
				T response = operation.apply(modelId);
				LOGGER.info(
						"AI provider selected capability={} model={} provider={} durationMs={}",
						capability,
						definition.modelId(),
						definition.providerId(),
						elapsedMillis(startedAt));
				return response;
			}
			catch (BusinessException exception) {
				if (!shouldFailOver(exception)) {
					throw exception;
				}
				lastFailure = exception;
				if (index + 1 < models.size()) {
					LOGGER.warn(
							"AI provider failover capability={} failedModel={} provider={} durationMs={} errorCode={} nextModel={}",
							capability,
							modelId,
							definition.providerId(),
							elapsedMillis(startedAt),
							exception.code(),
							models.get(index + 1));
				}
				else {
					LOGGER.warn(
							"AI provider route exhausted capability={} failedModel={} provider={} durationMs={} errorCode={}",
							capability,
							modelId,
							definition.providerId(),
							elapsedMillis(startedAt),
							exception.code());
				}
			}
		}
		if (lastFailure != null) {
			throw lastFailure;
		}
		throw new BusinessException(
				"AI_PROVIDER_ROUTE_EXHAUSTED",
				"No AI provider completed the " + capability + " request");
	}

	private static long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}

	private boolean shouldFailOver(BusinessException exception) {
		Boolean classifiedRetryable = AbstractAiProvider.retryable(exception);
		if (classifiedRetryable != null) {
			return classifiedRetryable;
		}
		String code = exception.code() == null ? "" : exception.code();
		return !code.startsWith("INVALID_")
				&& !code.startsWith("UNSUPPORTED_")
				&& !code.endsWith("_INTERRUPTED")
				&& !"TTS_TEXT_TOO_LONG".equals(code)
				&& !"PRONUNCIATION_AUDIO_TOO_LARGE".equals(code)
				&& !"PRONUNCIATION_AUDIO_TOO_LONG".equals(code)
				&& !"PRONUNCIATION_REFERENCE_TOO_LONG".equals(code);
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

	private Map<String, ? extends AbstractAiProvider> providers(AiCapability capability) {
		return switch (capability) {
			case REALTIME -> realtimeProviders;
			case LLM -> llmProviders;
			case SCORING -> scoringProviders;
			case TTS -> ttsProviders;
			case TRANSCRIPTION -> transcriptionProviders;
		};
	}

	private List<String> orderedModelIds(
			AiCapability capability,
			Set<String> registeredModelIds) {
		LinkedHashSet<String> ordered = new LinkedHashSet<>(route(capability));
		ordered.addAll(registeredModelIds);
		return List.copyOf(ordered);
	}

	private Byte[] boxAudio(byte[] audio) {
		if (audio == null) return null;
		Byte[] boxed = new Byte[audio.length];
		for (int index = 0; index < audio.length; index++) boxed[index] = audio[index];
		return boxed;
	}

	private byte[] unboxAudio(Byte[] audio) {
		if (audio == null) return null;
		byte[] unboxed = new byte[audio.length];
		for (int index = 0; index < audio.length; index++) {
			if (audio[index] == null) {
				throw new BusinessException("INVALID_AUDIO", "audio contains a null byte");
			}
			unboxed[index] = audio[index];
		}
		return unboxed;
	}

	private static List<String> parseRoute(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return normalizeRoute(List.of(value.split(",")));
	}

	private static List<String> normalizeRoute(List<String> modelIds) {
		LinkedHashSet<String> normalized = new LinkedHashSet<>();
		for (String modelId : modelIds) {
			String value = AbstractAiProvider.normalizeModelId(modelId);
			if (!value.isBlank()) {
				normalized.add(value);
			}
		}
		return List.copyOf(normalized);
	}
}
