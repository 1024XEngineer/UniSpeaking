package com.unispeaking.provider;

import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.provider.AiModelDefinition;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.provider.config.AiConfigurationStore;
import com.unispeaking.provider.config.AiModelConfiguration;
import com.unispeaking.provider.config.AiProviderCredentialStore;
import com.unispeaking.provider.config.AiRuntimeConfiguration;
import com.unispeaking.provider.usage.AiInvocationAttempt;
import com.unispeaking.provider.usage.AiInvocationLedger;
import com.unispeaking.service.auth.AuthService;
import java.math.BigDecimal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
	private AiConfigurationStore configurationStore;
	private AiInvocationLedger invocationLedger;
	private AuthService authService;
	private AiProviderCredentialStore credentialStore;

	@Autowired
	void configureDynamicRuntime(
			AiConfigurationStore configurationStore,
			AiInvocationLedger invocationLedger,
			AuthService authService,
			AiProviderCredentialStore credentialStore) {
		this.configurationStore = configurationStore;
		this.invocationLedger = invocationLedger;
		this.authService = authService;
		this.credentialStore = credentialStore;
	}

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
		AiRuntimeConfiguration runtime = runtimeConfiguration();
		if (!runtime.databaseBacked()) return models;
		return runtime.models().values().stream()
				.filter(this::available)
				.map(model -> new AiModelDefinition(
						model.modelId(), model.providerId(), model.capability(),
						model.modelId().equals(defaultModel(model.capability()))))
				.toList();
	}

	/** Models backed by adapters in this deployment, independent of database enablement. */
	public List<AiModelDefinition> deployedModels() {
		return models;
	}

	public AiModelDefinition getModel(String modelId) {
		AiRuntimeConfiguration runtime = runtimeConfiguration();
		if (runtime.databaseBacked()) {
			AiModelConfiguration configured = runtime.models().get(AbstractAiProvider.normalizeModelId(modelId));
			if (configured == null || !available(configured)) {
				throw new BusinessException("AI_MODEL_NOT_AVAILABLE", "AI model is disabled or unavailable: " + modelId);
			}
			return new AiModelDefinition(configured.modelId(), configured.providerId(), configured.capability(),
					configured.modelId().equals(defaultModel(configured.capability())));
		}
		AiModelDefinition definition = modelDefinitions.get(AbstractAiProvider.normalizeModelId(modelId));
		if (definition == null) {
			throw new BusinessException("AI_MODEL_NOT_FOUND", "AI model is not registered: " + modelId);
		}
		return definition;
	}

	public String defaultModel(AiCapability capability) {
		List<String> route = resolveRoute("default", capability);
		if (route == null || route.isEmpty()) {
			throw new BusinessException(
					"AI_DEFAULT_MODEL_NOT_FOUND",
					"No default AI model is registered for " + capability);
		}
		return route.getFirst();
	}

	public List<String> route(AiCapability capability) {
		return route("default", capability);
	}

	public List<String> route(String routeKey, AiCapability capability) {
		List<String> route = resolveRoute(routeKey, capability);
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
		return routeRealtime(automaticContext("realtime_connect"), requestedProvider, requestedModel, operation);
	}

	public <T> T routeRealtime(
			AiInvocationContext context,
			ProviderType requestedProvider,
			String requestedModel,
			BiFunction<String, RealtimeProvider, T> operation) {
		String model = AbstractAiProvider.normalizeModelId(requestedModel);
		List<String> models = model.isBlank()
				? route(context.routeKey(), AiCapability.REALTIME)
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
		return invokeMeasuredModels(
				context,
				AiCapability.REALTIME,
				models,
				modelId -> new AiProviderResponse<>(
						operation.apply(modelId, getRealtimeProvider(modelId)), null,
						new ProviderUsage(0, 0, 0, 0, 0, 0, "NONE")));
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

	public void recordRealtimeSession(
			String userId,
			String sessionId,
			String modelId,
			String providerRequestId,
			Instant startedAt,
			Instant endedAt) {
		if (invocationLedger == null || sessionId == null || sessionId.isBlank()
				|| modelId == null || modelId.isBlank() || startedAt == null || endedAt == null) return;
		double seconds = Math.max(0, java.time.Duration.between(startedAt, endedAt).toMillis() / 1000d);
		UUID stableId = UUID.nameUUIDFromBytes(("realtime-session:" + sessionId).getBytes(StandardCharsets.UTF_8));
		AiInvocationContext context = new AiInvocationContext(
				stableId, userId, sessionId, "realtime_session", "default");
		invocationLedger.record(new AiInvocationAttempt(
				stableId, context, 1, AiCapability.REALTIME,
				modelConfigurationForLedger(AbstractAiProvider.normalizeModelId(modelId), AiCapability.REALTIME),
				providerRequestId, startedAt, endedAt, Math.max(0, java.time.Duration.between(startedAt, endedAt).toMillis()),
				new ProviderUsage(0, 0, 0, 0, seconds, 0, "ESTIMATED"),
				"SUCCEEDED", null, false, null));
	}

	public String exchangeRealtimeSdp(String modelId, String offerSdp, String token) {
		return routeRealtime(
				automaticContext("realtime_connect"),
				null,
				modelId,
				(id, provider) -> provider.exchangeRealtimeSdp(id, offerSdp, token));
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
		return boxAudio(generateSpeechAudioBytes(automaticContext("tts"), modelId, text, token));
	}

	public Byte[] generateSpeechAudio(String text, String token) {
		return generateSpeechAudioRouted(text, token).response();
	}

	public RoutedResult<Byte[]> generateSpeechAudioRouted(String text, String token) {
		return generateSpeechAudioRouted(automaticContext("tts"), text, token);
	}

	public RoutedResult<Byte[]> generateSpeechAudioRouted(
			AiInvocationContext context, String text, String token) {
		return invokeRouteWithResult(
				context,
				AiCapability.TTS,
				modelId -> {
					AiProviderResponse<byte[]> measured = getTtsProvider(modelId)
							.generateSpeechAudioMeasured(text, credential(modelId, token));
					return new AiProviderResponse<>(boxAudio(measured.response()), measured.providerRequestId(), measured.usage());
				});
	}

	public byte[] generateSpeechAudioBytes(String modelId, String text, String token) {
		return generateSpeechAudioBytes(automaticContext("tts"), modelId, text, token);
	}

	public byte[] generateSpeechAudioBytes(String text, String token) {
		return unboxAudio(generateSpeechAudio(text, token));
	}

	public byte[] generateSpeechAudioBytes(
			AiInvocationContext context, String modelId, String text, String token) {
		if (modelId == null || modelId.isBlank()) {
			return unboxAudio(generateSpeechAudioRouted(context, text, token).response());
		}
		return invokeExplicitMeasured(context, AiCapability.TTS, modelId,
				id -> getTtsProvider(id).generateSpeechAudioMeasured(text, credential(id, token)));
	}

	public byte[] generateSpeechAudioBytes(
			String modelId,
			String text,
			String token,
			String voice) {
		return generateSpeechAudioBytes(automaticContext("tts"), modelId, text, token, voice);
	}

	public byte[] generateSpeechAudioBytes(
			AiInvocationContext context,
			String modelId,
			String text,
			String token,
			String voice) {
		if (modelId == null || modelId.isBlank()) {
			throw new BusinessException(
					"AI_TTS_MODEL_REQUIRED",
					"A voice-specific TTS request requires an explicit model");
		}
		return invokeExplicitMeasured(context, AiCapability.TTS, modelId,
				id -> getTtsProvider(id).generateSpeechAudioMeasured(
						text, credential(id, token), voice));
	}

	public String executeLlmTask(String modelId, String prompt, String token) {
		return executeLlmTask(automaticContext("llm"), modelId, prompt, token);
	}

	public String executeLlmTask(
			String modelId,
			String prompt,
			String token,
			LlmResponseFormat responseFormat) {
		return invokeExplicitMeasured(
				automaticContext("llm"),
				AiCapability.LLM,
				modelId,
				id -> getLlmProvider(id).executeLlmTaskMeasured(
						prompt, credential(id, token), responseFormat));
	}

	public String executeLlmTask(String prompt, String token) {
		return executeLlmTaskRouted(prompt, token).response();
	}

	public RoutedResult<String> executeLlmTaskRouted(String prompt, String token) {
		return executeLlmTaskRouted(automaticContext("llm"), prompt, token);
	}

	public RoutedResult<String> executeLlmTaskRouted(
			AiInvocationContext context, String prompt, String token) {
		return invokeRouteWithResult(
				context,
				AiCapability.LLM,
				modelId -> getLlmProvider(modelId).executeLlmTaskMeasured(prompt, credential(modelId, token)));
	}

	public String executeLlmTask(
			AiInvocationContext context, String modelId, String prompt, String token) {
		if (modelId == null || modelId.isBlank()) {
			return executeLlmTaskRouted(context, prompt, token).response();
		}
		return invokeExplicitMeasured(context, AiCapability.LLM, modelId,
				id -> getLlmProvider(id).executeLlmTaskMeasured(prompt, credential(id, token)));
	}

	public RoutedResult<String> executeLlmTaskRouted(
			String prompt,
			String token,
			LlmResponseFormat responseFormat) {
		return invokeRouteWithResult(
				automaticContext("llm"),
				AiCapability.LLM,
				modelId -> getLlmProvider(modelId).executeLlmTaskMeasured(
						prompt, credential(modelId, token), responseFormat));
	}

	public String convertAudioToText(String modelId, Byte[] audio, String token) {
		byte[] input = unboxAudio(audio);
		return invokeExplicitMeasured(automaticContext("transcription"), AiCapability.TRANSCRIPTION,
				modelId, id -> getTranscriptionProvider(id).convertAudioToTextMeasured(
						input, credential(id, token)));
	}

	public String convertAudioToText(Byte[] audio, String token) {
		return convertAudioToTextRouted(audio, token).response();
	}

	public RoutedResult<String> convertAudioToTextRouted(Byte[] audio, String token) {
		return convertAudioToTextRouted(automaticContext("transcription"), audio, token);
	}

	public RoutedResult<String> convertAudioToTextRouted(
			AiInvocationContext context, Byte[] audio, String token) {
		byte[] input = unboxAudio(audio);
		return invokeRouteWithResult(
				context,
				AiCapability.TRANSCRIPTION,
				modelId -> getTranscriptionProvider(modelId).convertAudioToTextMeasured(input, credential(modelId, token)));
	}

	public String evaluatePronunciation(
			String modelId,
			String text,
			Byte[] audio,
			String token) {
		byte[] input = unboxAudio(audio);
		return invokeExplicitMeasured(automaticContext("pronunciation_scoring"), AiCapability.SCORING,
				modelId, id -> getScoringProvider(id).evaluatePronunciationMeasured(
						text, input, credential(id, token)));
	}

	public String evaluatePronunciation(String text, Byte[] audio, String token) {
		return evaluatePronunciationRouted(text, audio, token).response();
	}

	public RoutedResult<String> evaluatePronunciationRouted(
			String text,
			Byte[] audio,
			String token) {
		return evaluatePronunciationRouted(automaticContext("pronunciation_scoring"), text, audio, token);
	}

	public RoutedResult<String> evaluatePronunciationRouted(
			AiInvocationContext context,
			String text,
			Byte[] audio,
			String token) {
		byte[] input = unboxAudio(audio);
		return invokeRouteWithResult(
				context,
				AiCapability.SCORING,
				modelId -> getScoringProvider(modelId).evaluatePronunciationMeasured(text, input, credential(modelId, token)));
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
			AiInvocationContext context,
			AiCapability capability,
			Function<String, AiProviderResponse<T>> operation) {
		return invokeMeasuredModels(
				context,
				capability,
				route(context.routeKey(), capability),
				modelId -> {
					AiProviderResponse<T> measured = operation.apply(modelId);
					AiModelDefinition definition = getModel(modelId);
					return new AiProviderResponse<>(new RoutedResult<>(
							definition.modelId(), definition.providerId(), capability, measured.response()),
							measured.providerRequestId(), measured.usage());
				});
	}

	private <T> T invokeExplicitMeasured(
			AiInvocationContext context,
			AiCapability capability,
			String modelId,
			Function<String, AiProviderResponse<T>> operation) {
		return invokeMeasuredModels(context, capability, List.of(modelId), operation);
	}

	private <T> T invokeMeasuredModels(
			AiInvocationContext suppliedContext,
			AiCapability capability,
			List<String> models,
			Function<String, AiProviderResponse<T>> operation) {
		AiInvocationContext context = suppliedContext == null
				? automaticContext(capability.name().toLowerCase(java.util.Locale.ROOT))
				: suppliedContext;
		BusinessException lastFailure = null;
		for (int index = 0; index < models.size(); index++) {
			String modelId = AbstractAiProvider.normalizeModelId(models.get(index));
			AiModelConfiguration model = modelConfiguration(modelId, capability);
			Instant startedAt = Instant.now();
			long startedNanos = System.nanoTime();
			AiModelDefinition definition = getModel(modelId);
			LOGGER.info(
					"AI provider attempt capability={} model={} provider={} attempt={}/{}",
					capability,
					definition.modelId(),
					definition.providerId(),
					index + 1,
					models.size());
			try {
				String dynamicCredential = credentialStore == null
						? null
						: credentialStore.credentialOrFallback(model.providerId(), null);
				AiProviderResponse<T> measured = ProviderCredentialOverride.call(
						dynamicCredential, () -> operation.apply(modelId));
				Instant completedAt = Instant.now();
				recordAttempt(context, index + 1, capability, model, measured.providerRequestId(),
						startedAt, completedAt, elapsedMillis(startedNanos), measured.usage(),
						"SUCCEEDED", null, false, index == 0 ? null : models.get(index - 1));
				LOGGER.info(
						"AI provider selected capability={} model={} provider={} durationMs={}",
						capability,
						definition.modelId(),
						definition.providerId(),
						elapsedMillis(startedNanos));
				return measured.response();
			}
			catch (BusinessException exception) {
				boolean retryable = shouldFailOver(exception);
				String providerRequestId = null;
				ProviderUsage usage = null;
				if (exception instanceof MeteredProviderException metered) {
					providerRequestId = metered.providerRequestId();
					usage = metered.usage();
				}
				Instant completedAt = Instant.now();
				recordAttempt(context, index + 1, capability, model, providerRequestId,
						startedAt, completedAt, elapsedMillis(startedNanos), usage,
						"FAILED", exception.code(), retryable,
						index == 0 ? null : models.get(index - 1));
				if (!retryable) {
					throw exception;
				}
				lastFailure = exception;
				if (index + 1 < models.size()) {
					LOGGER.warn(
							"AI provider failover capability={} failedModel={} provider={} durationMs={} errorCode={} nextModel={}",
							capability,
							modelId,
							definition.providerId(),
							elapsedMillis(startedNanos),
							exception.code(),
							models.get(index + 1));
				}
				else {
					LOGGER.warn(
							"AI provider route exhausted capability={} failedModel={} provider={} durationMs={} errorCode={}",
							capability,
							modelId,
							definition.providerId(),
							elapsedMillis(startedNanos),
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

	private void recordAttempt(
			AiInvocationContext context,
			int attemptNo,
			AiCapability capability,
			AiModelConfiguration model,
			String providerRequestId,
			Instant startedAt,
			Instant completedAt,
			long durationMs,
			ProviderUsage usage,
			String status,
			String errorCode,
			boolean retryable,
			String fallbackFromModelId) {
		if (invocationLedger == null) return;
		invocationLedger.record(new AiInvocationAttempt(
				null, context, attemptNo, capability, model, providerRequestId,
				startedAt, completedAt, durationMs, usage, status, errorCode,
				retryable, fallbackFromModelId));
	}

	private AiRuntimeConfiguration runtimeConfiguration() {
		return configurationStore == null
				? new AiRuntimeConfiguration(Map.of(), Map.of(), Map.of(), false)
				: configurationStore.load();
	}

	private List<String> resolveRoute(String routeKey, AiCapability capability) {
		AiRuntimeConfiguration runtime = runtimeConfiguration();
		if (!runtime.databaseBacked()) return modelRoutes.getOrDefault(capability, List.of());
		return runtime.route(routeKey, capability).stream()
				.map(AbstractAiProvider::normalizeModelId)
				.filter(modelId -> {
					AiModelConfiguration model = runtime.models().get(modelId);
					return model != null && model.capability() == capability && available(model, runtime);
				})
				.toList();
	}

	private boolean available(AiModelConfiguration model) {
		return available(model, runtimeConfiguration());
	}

	private boolean available(AiModelConfiguration model, AiRuntimeConfiguration runtime) {
		if (model == null || !model.enabled()) return false;
		var providerConfiguration = runtime.providers().get(model.providerId());
		if (providerConfiguration == null || !providerConfiguration.enabled()) return false;
		AbstractAiProvider adapter = providers(model.capability()).get(model.modelId());
		return adapter != null
				&& adapter.providerId().equalsIgnoreCase(providerConfiguration.adapterType());
	}

	private AiModelConfiguration modelConfiguration(String modelId, AiCapability capability) {
		AiRuntimeConfiguration runtime = runtimeConfiguration();
		if (runtime.databaseBacked()) {
			AiModelConfiguration model = runtime.models().get(modelId);
			if (model == null || model.capability() != capability || !available(model, runtime)) {
				throw new BusinessException("AI_MODEL_NOT_AVAILABLE", "AI model is disabled or unavailable: " + modelId);
			}
			return model;
		}
		AiModelDefinition definition = modelDefinitions.get(modelId);
		if (definition == null || definition.capability() != capability) {
			throw new BusinessException("AI_MODEL_NOT_FOUND", "AI model is not registered: " + modelId);
		}
		return new AiModelConfiguration(modelId, definition.providerId(), modelId, capability, true,
				"TOKENS", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "CNY");
	}

	private AiModelConfiguration modelConfigurationForLedger(String modelId, AiCapability capability) {
		AiRuntimeConfiguration runtime = runtimeConfiguration();
		if (runtime.databaseBacked()) {
			AiModelConfiguration model = runtime.models().get(modelId);
			if (model != null && model.capability() == capability) return model;
		}
		AiModelDefinition definition = modelDefinitions.get(modelId);
		if (definition == null || definition.capability() != capability) {
			throw new BusinessException("AI_MODEL_NOT_FOUND", "AI model is not registered: " + modelId);
		}
		return new AiModelConfiguration(modelId, definition.providerId(), modelId, capability, true,
				"AUDIO_MINUTES", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
				BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "CNY");
	}

	private AiInvocationContext automaticContext(String businessScene) {
		AiInvocationContext scoped = AiInvocationContexts.current();
		if (scoped != null) return scoped;
		String userId = null;
		try {
			if (authService != null) userId = authService.currentUserIdOrNull();
		}
		catch (RuntimeException ignored) {
			// Background work and tests may not have an authenticated request.
		}
		return AiInvocationContext.create(userId, null, businessScene);
	}

	private String credential(String modelId, String supplied) {
		if (supplied != null && !supplied.isBlank()) return supplied;
		AiModelDefinition model = getModel(modelId);
		return credentialStore == null ? supplied : credentialStore.credentialOrFallback(model.providerId(), supplied);
	}

	private static long elapsedMillis(long startedNanos) {
		return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
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
