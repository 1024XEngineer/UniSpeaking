package com.unispeaking.infrastructure.ai.aliyun;

import com.unispeaking.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.TtsProvider;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AliyunTtsProvider extends TtsProvider {

	private static final int MAX_TEXT_LENGTH = 5_000;
	private static final int MAX_JSON_RESPONSE_BYTES = 1024 * 1024;
	private static final int DEFAULT_MAX_AUDIO_BYTES = 10 * 1024 * 1024;

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final URI endpoint;
	private final String model;
	private final String voice;
	private final String format;
	private final int sampleRate;
	private final Duration readTimeout;
	private final int maxAudioBytes;

	@Autowired
	public AliyunTtsProvider(
			ObjectMapper objectMapper,
			@Value("${DASHSCOPE_API_KEY:}") String apiKey,
			@Value("${BAILIAN_WORKSPACE_ID:}") String workspaceId,
			@Value("${BAILIAN_REGION:cn-beijing}") String region,
			@Value("${ALIYUN_TTS_MODEL:cosyvoice-v3-flash}") String model,
			@Value("${ALIYUN_TTS_VOICE:loongemily_v3}") String voice,
			@Value("${ALIYUN_TTS_FORMAT:wav}") String format,
			@Value("${ALIYUN_TTS_SAMPLE_RATE:24000}") int sampleRate,
			@Value("${ALIYUN_TTS_CONNECT_TIMEOUT_SECONDS:10}") int connectTimeoutSeconds,
			@Value("${ALIYUN_TTS_READ_TIMEOUT_SECONDS:60}") int readTimeoutSeconds,
			@Value("${ALIYUN_TTS_MAX_AUDIO_BYTES:10485760}") int maxAudioBytes) {
		this(
				HttpClient.newBuilder()
						.connectTimeout(positiveDuration(connectTimeoutSeconds, "Aliyun TTS connect timeout"))
						.build(),
				objectMapper,
				apiKey,
				buildEndpoint(workspaceId, region),
				model,
				voice,
				format,
				sampleRate,
				positiveDuration(readTimeoutSeconds, "Aliyun TTS read timeout"),
				maxAudioBytes);
	}

	public AliyunTtsProvider(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			String apiKey,
			URI endpoint,
			String model,
			String voice,
			String format,
			int sampleRate,
			Duration readTimeout,
			int maxAudioBytes) {
		super("aliyun", Set.of(requiredText(model, "Aliyun TTS model")));
		this.httpClient = require(httpClient, "Aliyun TTS HTTP client");
		this.objectMapper = require(objectMapper, "Aliyun TTS JSON mapper");
		this.apiKey = trim(apiKey);
		this.endpoint = endpoint;
		this.model = requiredText(model, "Aliyun TTS model");
		this.voice = requiredText(voice, "Aliyun TTS voice");
		this.format = supportedFormat(format);
		this.sampleRate = supportedSampleRate(sampleRate);
		this.readTimeout = requirePositive(readTimeout, "Aliyun TTS read timeout");
		this.maxAudioBytes = maxAudioBytes > 0 ? maxAudioBytes : DEFAULT_MAX_AUDIO_BYTES;
	}

	@Override
	public Byte[] generateSpeechAudio(String text, String token) {
		if (apiKey.isBlank()) {
			throw retryableFailure(
					"ALIYUN_TTS_CREDENTIAL_MISSING",
					"Set DASHSCOPE_API_KEY before calling Aliyun TTS");
		}
		return boxAudio(synthesize(text, apiKey));
	}

	private byte[] synthesize(String textValue, String credential) {
		String text = trim(textValue);
		if (text.isBlank()) {
			throw nonRetryableFailure("INVALID_TTS_TEXT", "Speech synthesis text is required");
		}
		if (text.length() > MAX_TEXT_LENGTH) {
			throw nonRetryableFailure(
					"TTS_TEXT_TOO_LONG",
					"Speech synthesis text exceeds " + MAX_TEXT_LENGTH + " characters");
		}
		requireHttpsEndpoint(endpoint, "ALIYUN_TTS_ENDPOINT_INVALID");

		try {
			Map<String, Object> input = Map.of(
					"text", text,
					"voice", voice,
					"format", format,
					"sample_rate", sampleRate,
					"language_hints", List.of("en"));
			String requestBody = objectMapper.writeValueAsString(Map.of(
					"model", model,
					"input", input));
			HttpRequest synthesisRequest = HttpRequest.newBuilder()
					.uri(endpoint)
					.timeout(readTimeout)
					.header("Authorization", "Bearer " + credential)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(
							requestBody,
							StandardCharsets.UTF_8))
					.build();
			HttpResponse<byte[]> synthesisResponse = httpClient.send(
					synthesisRequest,
					limitedBodyHandler(
							MAX_JSON_RESPONSE_BYTES,
							"ALIYUN_TTS_RESPONSE_TOO_LARGE",
							"Aliyun TTS JSON response exceeds the configured limit"));
			if (!successful(synthesisResponse.statusCode())) {
				throw httpError("ALIYUN_TTS_REQUEST_FAILED", synthesisResponse.statusCode());
			}
			String responseBody = new String(
					synthesisResponse.body(),
					StandardCharsets.UTF_8);
			URI audioUri = audioUri(responseBody);
			HttpRequest audioRequest = HttpRequest.newBuilder()
					.uri(audioUri)
					.timeout(readTimeout)
					.GET()
					.build();
			HttpResponse<byte[]> audioResponse = httpClient.send(
					audioRequest,
					limitedBodyHandler(
							maxAudioBytes,
							"ALIYUN_TTS_AUDIO_TOO_LARGE",
							"Aliyun TTS audio exceeds the configured limit"));
			if (!successful(audioResponse.statusCode())) {
				throw httpError(
						"ALIYUN_TTS_AUDIO_DOWNLOAD_FAILED",
						audioResponse.statusCode());
			}
			byte[] audio = audioResponse.body();
			if (audio.length == 0) {
				throw retryableFailure(
						"ALIYUN_TTS_AUDIO_EMPTY",
						"Aliyun TTS returned an empty audio file");
			}
			return audio;
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (JacksonException exception) {
			throw retryableFailure(
					"ALIYUN_TTS_RESPONSE_INVALID",
					"Aliyun TTS response is not valid JSON");
		}
		catch (IOException exception) {
			BusinessException bodyError = businessCause(exception);
			if (bodyError != null) {
				throw bodyError;
			}
			throw retryableFailure(
					"ALIYUN_TTS_IO_ERROR",
					"Failed to call Aliyun TTS");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw nonRetryableFailure(
					"ALIYUN_TTS_INTERRUPTED",
					"Aliyun TTS call was interrupted");
		}
	}

	private URI audioUri(String responseBody) throws JacksonException {
		JsonNode root = objectMapper.readTree(responseBody);
		String audioUrl = root.path("output")
				.path("audio")
				.path("url")
				.asString("")
				.trim();
		if (audioUrl.isBlank()) {
			throw retryableFailure(
					"ALIYUN_TTS_AUDIO_URL_MISSING",
					"Aliyun TTS response does not contain an audio URL");
		}
		URI uri;
		try {
			uri = URI.create(audioUrl);
		}
		catch (IllegalArgumentException exception) {
			throw retryableFailure(
					"ALIYUN_TTS_AUDIO_URL_INVALID",
					"Aliyun TTS returned an invalid audio URL");
		}
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		boolean trustedScheme = "https".equalsIgnoreCase(uri.getScheme())
				|| "http".equalsIgnoreCase(uri.getScheme());
		boolean trustedHost = host.endsWith(".aliyuncs.com");
		if (!uri.isAbsolute()
				|| !trustedScheme
				|| !trustedHost
				|| uri.getUserInfo() != null) {
			throw retryableFailure(
					"ALIYUN_TTS_AUDIO_URL_UNTRUSTED",
					"Aliyun TTS returned an untrusted audio URL");
		}
		if ("http".equalsIgnoreCase(uri.getScheme())) {
			try {
				uri = URI.create("https:" + uri.toString().substring("http:".length()));
			}
			catch (IllegalArgumentException exception) {
				throw retryableFailure(
						"ALIYUN_TTS_AUDIO_URL_INVALID",
						"Aliyun TTS returned an invalid audio URL");
			}
		}
		return uri;
	}

	private boolean successful(int statusCode) {
		return statusCode >= 200 && statusCode < 300;
	}

	private BusinessException httpError(String errorCode, int statusCode) {
		return retryableFailure(
				errorCode,
				"Aliyun TTS returned HTTP " + statusCode);
	}

	private static URI buildEndpoint(String workspaceId, String region) {
		String workspace = trim(workspaceId);
		String endpointRegion = trim(region);
		if (!safeEndpointComponent(workspace) || !safeEndpointComponent(endpointRegion)) {
			return null;
		}
		return URI.create("https://" + workspace + "." + endpointRegion
				+ ".maas.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer");
	}

	private static void requireHttpsEndpoint(URI uri, String errorCode) {
		String host = uri == null || uri.getHost() == null
				? ""
				: uri.getHost().toLowerCase(Locale.ROOT);
		if (uri == null
				|| !uri.isAbsolute()
				|| !"https".equalsIgnoreCase(uri.getScheme())
				|| !host.endsWith(".maas.aliyuncs.com")
				|| uri.getUserInfo() != null
				|| uri.getPort() != -1
				|| !"/api/v1/services/audio/tts/SpeechSynthesizer".equals(uri.getPath())
				|| uri.getRawQuery() != null
				|| uri.getRawFragment() != null) {
			throw retryableFailure(
					errorCode,
					"Aliyun TTS endpoint must be the trusted Aliyun speech synthesis URL");
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

	private static boolean safeEndpointComponent(String value) {
		return !value.isBlank() && value.matches("[A-Za-z0-9-]+");
	}

	private static String contentType(String audioFormat) {
		return switch (audioFormat) {
			case "mp3" -> "audio/mpeg";
			case "wav" -> "audio/wav";
			case "pcm" -> "audio/L16";
			case "opus" -> "audio/opus";
			default -> "application/octet-stream";
		};
	}

	private static String supportedFormat(String value) {
		String audioFormat = trim(value).toLowerCase(Locale.ROOT);
		if (!Set.of("mp3", "wav", "pcm", "opus").contains(audioFormat)) {
			throw new IllegalArgumentException("Unsupported Aliyun TTS audio format: " + value);
		}
		return audioFormat;
	}

	private static int supportedSampleRate(int value) {
		if (!Set.of(8_000, 16_000, 22_050, 24_000, 44_100, 48_000).contains(value)) {
			throw new IllegalArgumentException("Unsupported Aliyun TTS sample rate: " + value);
		}
		return value;
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
					body.completeExceptionally(retryableFailure(errorCode, errorMessage));
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
}
