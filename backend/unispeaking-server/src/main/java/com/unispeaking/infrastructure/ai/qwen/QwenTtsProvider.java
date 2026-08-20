package com.unispeaking.infrastructure.ai.qwen;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.provider.AiProviderResponse;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.TtsProvider;
import com.unispeaking.provider.ProviderCredentialOverride;
import com.unispeaking.provider.ProviderUsage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class QwenTtsProvider extends TtsProvider {

	private static final Map<String, String> PRODUCT_VOICE_MAPPING = Map.of(
			"Katerina", "Katerina",
			"Aiden", "Aiden",
			"Dolce", "Dolce",
			"Harvey", "Neil",
			"Raymond", "Ryan",
			"Tina", "Serena");

	private static final int MAX_TEXT_LENGTH = 5_000;
	private static final int DEFAULT_MAX_RESPONSE_BYTES = 1024 * 1024;
	private static final int DEFAULT_MAX_AUDIO_BYTES = 10 * 1024 * 1024;
	private static final int MAX_CACHE_ENTRIES = 256;
	private static final long CACHE_TTL_NANOS = TimeUnit.MINUTES.toNanos(30);
	private static final String DEFAULT_ENDPOINT =
			"https://dashscope.aliyuncs.com/api/v1/services/aigc/"
					+ "multimodal-generation/generation";

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final URI endpoint;
	private final String model;
	private final String voice;
	private final String languageType;
	private final Duration readTimeout;
	private final int maxResponseBytes;
	private final int maxAudioBytes;
	private final Map<String, CachedAudio> audioCache = new ConcurrentHashMap<>();
	private final Map<String, CompletableFuture<byte[]>> inFlightAudio =
			new ConcurrentHashMap<>();
	private final ThreadLocal<String> requestIdCapture = new ThreadLocal<>();

	@Autowired
	public QwenTtsProvider(
			ObjectMapper objectMapper,
			@Value("${DASHSCOPE_API_KEY:}") String apiKey,
			@Value("${QWEN_TTS_ENDPOINT:" + DEFAULT_ENDPOINT + "}") String endpoint,
			@Value("${QWEN_TTS_MODEL:qwen3-tts-flash}") String model,
			@Value("${QWEN_TTS_VOICE:Aiden}") String voice,
			@Value("${QWEN_TTS_LANGUAGE_TYPE:English}") String languageType,
			@Value("${QWEN_TTS_CONNECT_TIMEOUT_SECONDS:10}") int connectTimeoutSeconds,
			@Value("${QWEN_TTS_READ_TIMEOUT_SECONDS:60}") int readTimeoutSeconds,
			@Value("${QWEN_TTS_MAX_RESPONSE_BYTES:1048576}") int maxResponseBytes,
			@Value("${QWEN_TTS_MAX_AUDIO_BYTES:10485760}") int maxAudioBytes) {
		this(
				HttpClient.newBuilder()
						.connectTimeout(positiveDuration(
								connectTimeoutSeconds,
								"Qwen TTS connect timeout"))
						.build(),
				objectMapper,
				apiKey,
				parseUri(endpoint),
				model,
				voice,
				languageType,
				positiveDuration(readTimeoutSeconds, "Qwen TTS read timeout"),
				maxResponseBytes,
				maxAudioBytes);
	}

	public QwenTtsProvider(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			String apiKey,
			URI endpoint,
			String model,
			String voice,
			String languageType,
			Duration readTimeout,
			int maxAudioBytes) {
		this(
				httpClient,
				objectMapper,
				apiKey,
				endpoint,
				model,
				voice,
				languageType,
				readTimeout,
				DEFAULT_MAX_RESPONSE_BYTES,
				maxAudioBytes);
	}

	public QwenTtsProvider(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			String apiKey,
			URI endpoint,
			String model,
			String voice,
			String languageType,
			Duration readTimeout,
			int maxResponseBytes,
			int maxAudioBytes) {
		super("qwen", Set.of(requiredText(model, "Qwen TTS model")));
		this.httpClient = require(httpClient, "Qwen TTS HTTP client");
		this.objectMapper = require(objectMapper, "Qwen TTS JSON mapper");
		this.apiKey = trim(apiKey);
		this.endpoint = require(endpoint, "Qwen TTS endpoint");
		this.model = requiredText(model, "Qwen TTS model");
		this.voice = requiredText(voice, "Qwen TTS voice");
		this.languageType = requiredText(languageType, "Qwen TTS language type");
		this.readTimeout = requirePositive(readTimeout, "Qwen TTS read timeout");
		this.maxResponseBytes = positiveLimit(
				maxResponseBytes,
				DEFAULT_MAX_RESPONSE_BYTES);
		this.maxAudioBytes = positiveLimit(maxAudioBytes, DEFAULT_MAX_AUDIO_BYTES);
	}

	@Override
	public byte[] generateSpeechAudio(String text, String token) {
		return generateSpeechAudio(text, token, voice);
	}

	@Override
	public byte[] generateSpeechAudio(String text, String token, String requestedVoice) {
		String credential = ProviderCredentialOverride.currentOr("apiKey", apiKey);
		if (credential.isBlank()) {
			throw retryableFailure(
					"QWEN_TTS_CREDENTIAL_MISSING",
					"Set DASHSCOPE_API_KEY before calling Qwen TTS");
		}
		String normalizedText = trim(text);
		String qwenVoice = resolveVoice(requestedVoice);
		return cachedSynthesize(normalizedText, credential, qwenVoice);
	}

	@Override
	public AiProviderResponse<byte[]> generateSpeechAudioMeasured(String text, String token) {
		return generateSpeechAudioMeasured(text, token, voice);
	}

	@Override
	public AiProviderResponse<byte[]> generateSpeechAudioMeasured(
			String text,
			String token,
			String requestedVoice) {
		requestIdCapture.remove();
		try {
			byte[] audio = generateSpeechAudio(text, token, requestedVoice);
			String requestId = requestIdCapture.get();
			ProviderUsage usage = requestId == null
					? new ProviderUsage(0, 0, 0, 0, 0, 0, "NONE")
					: ProviderUsage.tts(text, audio);
			return new AiProviderResponse<>(audio, requestId, usage);
		}
		catch (BusinessException exception) {
			String requestId = requestIdCapture.get();
			if (requestId != null && !requestId.isBlank()) {
				throw meteredFailure(exception, requestId, ProviderUsage.ttsInput(text));
			}
			throw exception;
		}
		finally {
			requestIdCapture.remove();
		}
	}

	private byte[] cachedSynthesize(String text, String credential, String qwenVoice) {
		String cacheKey = qwenVoice + ":" + text;
		long now = System.nanoTime();
		CachedAudio cached = audioCache.get(cacheKey);
		if (cached != null && now - cached.createdAtNanos() < CACHE_TTL_NANOS) {
			return cached.audio();
		}
		if (cached != null) {
			audioCache.remove(cacheKey, cached);
		}

		CompletableFuture<byte[]> pending = new CompletableFuture<>();
		CompletableFuture<byte[]> existing = inFlightAudio.putIfAbsent(cacheKey, pending);
		if (existing != null) {
			try {
				return existing.join();
			}
			catch (CompletionException exception) {
				if (exception.getCause() instanceof RuntimeException runtimeException) {
					throw runtimeException;
				}
				throw exception;
			}
		}

		try {
			byte[] audio = synthesize(text, credential, qwenVoice);
			cacheAudio(cacheKey, audio, System.nanoTime());
			pending.complete(audio);
			return audio;
		}
		catch (RuntimeException exception) {
			pending.completeExceptionally(exception);
			throw exception;
		}
		finally {
			inFlightAudio.remove(cacheKey, pending);
		}
	}

	private void cacheAudio(String text, byte[] audio, long createdAtNanos) {
		if (audioCache.size() >= MAX_CACHE_ENTRIES) {
			long now = System.nanoTime();
			audioCache.entrySet().removeIf(
					entry -> now - entry.getValue().createdAtNanos() >= CACHE_TTL_NANOS);
		}
		if (audioCache.size() >= MAX_CACHE_ENTRIES) {
			audioCache.keySet().stream().findFirst().ifPresent(audioCache::remove);
		}
		audioCache.put(text, new CachedAudio(audio, createdAtNanos));
	}

	private byte[] synthesize(String textValue, String credential, String qwenVoice) {
		String text = trim(textValue);
		if (text.isBlank()) {
			throw nonRetryableFailure(
					"INVALID_TTS_TEXT",
					"Speech synthesis text is required");
		}
		if (text.length() > MAX_TEXT_LENGTH) {
			throw nonRetryableFailure(
					"TTS_TEXT_TOO_LONG",
					"Qwen speech synthesis text exceeds "
							+ MAX_TEXT_LENGTH + " characters");
		}
		requireTrustedEndpoint();

		try {
			Map<String, Object> input = Map.of(
					"text", text,
					"voice", qwenVoice,
					"language_type", languageType);
			HttpRequest synthesisRequest = HttpRequest.newBuilder()
					.uri(endpoint)
					.timeout(readTimeout)
					.header("Authorization", "Bearer " + credential)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(
							objectMapper.writeValueAsString(Map.of(
									"model", model,
									"input", input)),
							StandardCharsets.UTF_8))
					.build();
			HttpResponse<byte[]> synthesisResponse = httpClient.send(
					synthesisRequest,
					limitedBodyHandler(
							maxResponseBytes,
							"QWEN_TTS_RESPONSE_TOO_LARGE",
							"Qwen TTS response exceeds the configured limit"));
			if (!successful(synthesisResponse.statusCode())) {
				throw httpError(
						"QWEN_TTS_REQUEST_FAILED",
						synthesisResponse.statusCode());
			}
			requestIdCapture.set(officialRequestId(synthesisResponse));
			URI audioUri = audioUri(synthesisResponse.body());
			HttpResponse<byte[]> audioResponse = httpClient.send(
					HttpRequest.newBuilder()
							.uri(audioUri)
							.timeout(readTimeout)
							.GET()
							.build(),
					limitedBodyHandler(
							maxAudioBytes,
							"QWEN_TTS_AUDIO_TOO_LARGE",
							"Qwen TTS audio exceeds the configured limit"));
			if (!successful(audioResponse.statusCode())) {
				throw httpError(
						"QWEN_TTS_AUDIO_DOWNLOAD_FAILED",
						audioResponse.statusCode());
			}
			byte[] audio = audioResponse.body();
			requireWav(audio);
			return audio;
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (JacksonException exception) {
			throw retryableFailure(
					"QWEN_TTS_RESPONSE_INVALID",
					"Qwen TTS response is not valid JSON");
		}
		catch (IOException exception) {
			BusinessException bodyError = businessCause(exception);
			if (bodyError != null) {
				throw bodyError;
			}
			throw retryableFailure(
					"QWEN_TTS_IO_ERROR",
					"Failed to call Qwen TTS");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw nonRetryableFailure(
					"QWEN_TTS_INTERRUPTED",
					"Qwen TTS call was interrupted");
		}
	}

	private String officialRequestId(HttpResponse<byte[]> response) throws JacksonException {
		String bodyRequestId = objectMapper.readTree(
				new String(response.body(), StandardCharsets.UTF_8))
				.path("request_id")
				.asString("")
				.trim();
		if (!bodyRequestId.isBlank()) return bodyRequestId;
		String header = response.headers().firstValue("x-request-id")
				.or(() -> response.headers().firstValue("x-dashscope-request-id"))
				.orElse("")
				.trim();
		return header.isBlank() ? null : header;
	}

	private String resolveVoice(String requestedVoice) {
		String candidate = trim(requestedVoice);
		if (candidate.isBlank()) return voice;
		return PRODUCT_VOICE_MAPPING.getOrDefault(candidate, voice);
	}

	private URI audioUri(byte[] responseBody) throws JacksonException {
		JsonNode root = objectMapper.readTree(
				new String(responseBody, StandardCharsets.UTF_8));
		String audioUrl = root.path("output")
				.path("audio")
				.path("url")
				.asString("")
				.trim();
		if (audioUrl.isBlank()) {
			throw retryableFailure(
					"QWEN_TTS_AUDIO_URL_MISSING",
					"Qwen TTS response does not contain an audio URL");
		}
		URI uri = parseUri(audioUrl);
		String host = normalizedHost(uri);
		if (!uri.isAbsolute()
				|| !Set.of("http", "https").contains(
						trim(uri.getScheme()).toLowerCase(Locale.ROOT))
				|| !host.endsWith(".aliyuncs.com")
				|| uri.getUserInfo() != null
				|| uri.getPort() != -1) {
			throw retryableFailure(
					"QWEN_TTS_AUDIO_URL_UNTRUSTED",
					"Qwen TTS returned an untrusted audio URL");
		}
		if ("http".equalsIgnoreCase(uri.getScheme())) {
			return parseUri("https:" + uri.toString().substring("http:".length()));
		}
		return uri;
	}

	private void requireTrustedEndpoint() {
		String host = normalizedHost(endpoint);
		if (!endpoint.isAbsolute()
				|| !"https".equalsIgnoreCase(endpoint.getScheme())
				|| !Set.of(
						"dashscope.aliyuncs.com",
						"dashscope-intl.aliyuncs.com").contains(host)
				|| endpoint.getUserInfo() != null
				|| endpoint.getPort() != -1
				|| !"/api/v1/services/aigc/multimodal-generation/generation"
						.equals(endpoint.getPath())
				|| endpoint.getRawQuery() != null
				|| endpoint.getRawFragment() != null) {
			throw retryableFailure(
					"QWEN_TTS_ENDPOINT_INVALID",
					"Qwen TTS endpoint must be the trusted DashScope generation URL");
		}
	}

	private void requireWav(byte[] audio) {
		if (audio == null
				|| audio.length < 12
				|| audio[0] != 'R'
				|| audio[1] != 'I'
				|| audio[2] != 'F'
				|| audio[3] != 'F'
				|| audio[8] != 'W'
				|| audio[9] != 'A'
				|| audio[10] != 'V'
				|| audio[11] != 'E') {
			throw retryableFailure(
					"QWEN_TTS_AUDIO_INVALID",
					"Qwen TTS did not return a WAV audio file");
		}
	}

	private static HttpResponse.BodyHandler<byte[]> limitedBodyHandler(
			int limit,
			String errorCode,
			String errorMessage) {
		return responseInfo -> new LimitedBodySubscriber(
				limit,
				errorCode,
				errorMessage);
	}

	private static BusinessException businessCause(Throwable throwable) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current instanceof BusinessException businessException) {
				return businessException;
			}
		}
		return null;
	}

	private static boolean successful(int statusCode) {
		return statusCode >= 200 && statusCode < 300;
	}

	private static BusinessException httpError(String errorCode, int statusCode) {
		return retryableFailure(
				errorCode,
				"Qwen TTS returned HTTP " + statusCode);
	}

	private static URI parseUri(String value) {
		try {
			return URI.create(trim(value));
		}
		catch (IllegalArgumentException exception) {
			throw retryableFailure(
					"QWEN_TTS_URL_INVALID",
					"Qwen TTS returned or configured an invalid URL");
		}
	}

	private static String normalizedHost(URI uri) {
		return uri == null || uri.getHost() == null
				? ""
				: uri.getHost().toLowerCase(Locale.ROOT);
	}

	private static int positiveLimit(int value, int defaultValue) {
		return value > 0 ? value : defaultValue;
	}

	private static Duration positiveDuration(int seconds, String name) {
		if (seconds <= 0) {
			throw new IllegalArgumentException(name + " must be greater than zero");
		}
		return Duration.ofSeconds(seconds);
	}

	private static Duration requirePositive(Duration duration, String name) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			throw new IllegalArgumentException(name + " must be greater than zero");
		}
		return duration;
	}

	private static String requiredText(String value, String name) {
		String result = trim(value);
		if (result.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
		return result;
	}

	private static <T> T require(T value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " is required");
		}
		return value;
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static final class LimitedBodySubscriber
			implements HttpResponse.BodySubscriber<byte[]> {

		private final int limit;
		private final String errorCode;
		private final String errorMessage;
		private final ByteArrayOutputStream bytes;
		private final CompletableFuture<byte[]> body = new CompletableFuture<>();
		private Flow.Subscription subscription;

		private LimitedBodySubscriber(int limit, String errorCode, String errorMessage) {
			this.limit = limit;
			this.errorCode = errorCode;
			this.errorMessage = errorMessage;
			this.bytes = new ByteArrayOutputStream(Math.min(limit, 8_192));
		}

		@Override
		public CompletionStage<byte[]> getBody() {
			return body;
		}

		@Override
		public void onSubscribe(Flow.Subscription subscription) {
			if (this.subscription != null) {
				subscription.cancel();
				return;
			}
			this.subscription = subscription;
			subscription.request(1);
		}

		@Override
		public void onNext(List<ByteBuffer> items) {
			if (body.isDone()) {
				return;
			}
			for (ByteBuffer item : items) {
				if (item.remaining() > limit - bytes.size()) {
					subscription.cancel();
					body.completeExceptionally(retryableFailure(
							errorCode,
							errorMessage));
					return;
				}
				byte[] chunk = new byte[item.remaining()];
				item.get(chunk);
				bytes.writeBytes(chunk);
			}
			subscription.request(1);
		}

		@Override
		public void onError(Throwable throwable) {
			body.completeExceptionally(throwable);
		}

		@Override
		public void onComplete() {
			body.complete(bytes.toByteArray());
		}
	}

	private record CachedAudio(byte[] audio, long createdAtNanos) {
	}
}
