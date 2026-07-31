package com.unispeaking.infrastructure.ai.minimax;

import com.unispeaking.common.exception.BusinessException;
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
import java.util.HexFormat;
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
public class MiniMaxTtsProvider extends TtsProvider {

	private static final int MAX_TEXT_LENGTH = 10_000;
	private static final int DEFAULT_MAX_AUDIO_BYTES = 10 * 1024 * 1024;
	private static final int JSON_OVERHEAD_BYTES = 1024 * 1024;
	private static final Set<String> TRUSTED_HOSTS = Set.of(
			"api.minimaxi.com",
			"api-bj.minimaxi.com",
			"api.minimax.io",
			"api-uw.minimax.io");

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final URI endpoint;
	private final String model;
	private final String voice;
	private final String format;
	private final int sampleRate;
	private final int bitrate;
	private final Duration readTimeout;
	private final int maxAudioBytes;

	@Autowired
	public MiniMaxTtsProvider(
			ObjectMapper objectMapper,
			@Value("${MINIMAX_API_KEY:}") String apiKey,
			@Value("${MINIMAX_TTS_ENDPOINT:https://api.minimaxi.com/v1/t2a_v2}")
			String endpoint,
			@Value("${MINIMAX_TTS_MODEL:speech-2.8-hd}") String model,
			@Value("${MINIMAX_TTS_VOICE:male-qn-qingse}") String voice,
			@Value("${MINIMAX_TTS_FORMAT:wav}") String format,
			@Value("${MINIMAX_TTS_SAMPLE_RATE:32000}") int sampleRate,
			@Value("${MINIMAX_TTS_BITRATE:128000}") int bitrate,
			@Value("${MINIMAX_TTS_CONNECT_TIMEOUT_SECONDS:10}") int connectTimeoutSeconds,
			@Value("${MINIMAX_TTS_READ_TIMEOUT_SECONDS:60}") int readTimeoutSeconds,
			@Value("${MINIMAX_TTS_MAX_AUDIO_BYTES:10485760}") int maxAudioBytes) {
		this(
				HttpClient.newBuilder()
						.connectTimeout(positiveDuration(
								connectTimeoutSeconds,
								"MiniMax TTS connect timeout"))
						.build(),
				objectMapper,
				apiKey,
				parseUri(endpoint),
				model,
				voice,
				format,
				sampleRate,
				bitrate,
				positiveDuration(readTimeoutSeconds, "MiniMax TTS read timeout"),
				maxAudioBytes);
	}

	public MiniMaxTtsProvider(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			String apiKey,
			URI endpoint,
			String model,
			String voice,
			String format,
			int sampleRate,
			int bitrate,
			Duration readTimeout,
			int maxAudioBytes) {
		super("minimax", Set.of(requiredText(model, "MiniMax TTS model")));
		this.httpClient = require(httpClient, "MiniMax TTS HTTP client");
		this.objectMapper = require(objectMapper, "MiniMax TTS JSON mapper");
		this.apiKey = trim(apiKey);
		this.endpoint = endpoint;
		this.model = requiredText(model, "MiniMax TTS model");
		this.voice = requiredText(voice, "MiniMax TTS voice");
		this.format = supportedFormat(format);
		this.sampleRate = supportedSampleRate(sampleRate);
		this.bitrate = supportedBitrate(bitrate);
		this.readTimeout = requirePositive(readTimeout, "MiniMax TTS read timeout");
		this.maxAudioBytes = maxAudioBytes > 0 ? maxAudioBytes : DEFAULT_MAX_AUDIO_BYTES;
	}

	@Override
	public Byte[] generateSpeechAudio(String text, String token) {
		if (apiKey.isBlank()) {
			throw retryableFailure(
					"MINIMAX_TTS_CREDENTIAL_MISSING",
					"Set MINIMAX_API_KEY before calling MiniMax TTS");
		}
		return boxAudio(synthesize(text, apiKey));
	}

	private byte[] synthesize(String textValue, String credential) {
		String text = trim(textValue);
		if (text.isBlank()) {
			throw nonRetryableFailure("INVALID_TTS_TEXT", "Speech synthesis text is required");
		}
		if (text.length() >= MAX_TEXT_LENGTH) {
			throw nonRetryableFailure(
					"TTS_TEXT_TOO_LONG",
					"MiniMax speech synthesis text must be shorter than "
							+ MAX_TEXT_LENGTH + " characters");
		}
		requireTrustedEndpoint();

		try {
			Map<String, Object> voiceSetting = Map.of(
					"voice_id", voice,
					"speed", 1,
					"vol", 1,
					"pitch", 0);
			Map<String, Object> audioSetting = Map.of(
					"sample_rate", sampleRate,
					"bitrate", bitrate,
					"format", format,
					"channel", 1);
			Map<String, Object> body = Map.of(
					"model", model,
					"text", text,
					"stream", false,
					"output_format", "hex",
					"language_boost", "auto",
					"voice_setting", voiceSetting,
					"audio_setting", audioSetting);
			HttpRequest httpRequest = HttpRequest.newBuilder()
					.uri(endpoint)
					.timeout(readTimeout)
					.header("Authorization", "Bearer " + credential)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(
							objectMapper.writeValueAsString(body),
							StandardCharsets.UTF_8))
					.build();
			HttpResponse<byte[]> response = httpClient.send(
					httpRequest,
					limitedBodyHandler(
							maxJsonResponseBytes(),
							"MINIMAX_TTS_RESPONSE_TOO_LARGE",
							"MiniMax TTS response exceeds the configured limit"));
			if (!successful(response.statusCode())) {
				throw retryableFailure(
						"MINIMAX_TTS_REQUEST_FAILED",
						"MiniMax TTS returned HTTP " + response.statusCode());
			}
			byte[] audio = parseAudio(response.body());
			return audio;
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (JacksonException exception) {
			throw retryableFailure(
					"MINIMAX_TTS_RESPONSE_INVALID",
					"MiniMax TTS response is not valid JSON");
		}
		catch (IOException exception) {
			BusinessException bodyError = businessCause(exception);
			if (bodyError != null) {
				throw bodyError;
			}
			throw retryableFailure(
					"MINIMAX_TTS_IO_ERROR",
					"Failed to call MiniMax TTS");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw nonRetryableFailure(
					"MINIMAX_TTS_INTERRUPTED",
					"MiniMax TTS call was interrupted");
		}
	}

	private byte[] parseAudio(byte[] responseBody) throws JacksonException {
		JsonNode root = objectMapper.readTree(
				new String(responseBody, StandardCharsets.UTF_8));
		int providerStatus = root.path("base_resp")
				.path("status_code")
				.asInt(Integer.MIN_VALUE);
		if (providerStatus != 0) {
			throw retryableFailure(
					"MINIMAX_TTS_REQUEST_FAILED",
					"MiniMax TTS returned provider status " + providerStatus);
		}
		String audioHex = root.path("data").path("audio").asString("").trim();
		if (audioHex.isBlank()) {
			throw retryableFailure(
					"MINIMAX_TTS_AUDIO_EMPTY",
					"MiniMax TTS returned no audio");
		}
		byte[] audio;
		try {
			audio = HexFormat.of().parseHex(audioHex);
		}
		catch (IllegalArgumentException exception) {
			throw retryableFailure(
					"MINIMAX_TTS_AUDIO_INVALID",
					"MiniMax TTS returned invalid hex audio");
		}
		if (audio.length > maxAudioBytes) {
			throw retryableFailure(
					"MINIMAX_TTS_AUDIO_TOO_LARGE",
					"MiniMax TTS audio exceeds the configured limit");
		}
		if (audio.length == 0) {
			throw retryableFailure(
					"MINIMAX_TTS_AUDIO_EMPTY",
					"MiniMax TTS returned an empty audio file");
		}
		return audio;
	}

	private int maxJsonResponseBytes() {
		long limit = (long) maxAudioBytes * 2 + JSON_OVERHEAD_BYTES;
		return (int) Math.min(Integer.MAX_VALUE, limit);
	}

	private void requireTrustedEndpoint() {
		String host = endpoint == null || endpoint.getHost() == null
				? ""
				: endpoint.getHost().toLowerCase(Locale.ROOT);
		if (endpoint == null
				|| !endpoint.isAbsolute()
				|| !"https".equalsIgnoreCase(endpoint.getScheme())
				|| !TRUSTED_HOSTS.contains(host)
				|| endpoint.getUserInfo() != null
				|| endpoint.getPort() != -1
				|| !"/v1/t2a_v2".equals(endpoint.getPath())
				|| endpoint.getRawQuery() != null
				|| endpoint.getRawFragment() != null) {
			throw retryableFailure(
					"MINIMAX_TTS_ENDPOINT_INVALID",
					"MiniMax TTS endpoint must be a trusted T2A v2 URL");
		}
	}

	private static boolean successful(int statusCode) {
		return statusCode >= 200 && statusCode < 300;
	}

	private static URI parseUri(String value) {
		try {
			return URI.create(trim(value));
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static HttpResponse.BodyHandler<byte[]> limitedBodyHandler(
			int limit,
			String errorCode,
			String errorMessage) {
		return responseInfo -> new LimitedBodySubscriber(limit, errorCode, errorMessage);
	}

	private static BusinessException businessCause(Throwable throwable) {
		for (Throwable current = throwable; current != null; current = current.getCause()) {
			if (current instanceof BusinessException businessException) {
				return businessException;
			}
		}
		return null;
	}

	private static String contentType(String audioFormat) {
		return switch (audioFormat) {
			case "mp3" -> "audio/mpeg";
			case "wav" -> "audio/wav";
			case "flac" -> "audio/flac";
			case "pcm" -> "audio/L16";
			default -> "application/octet-stream";
		};
	}

	private static String supportedFormat(String value) {
		String audioFormat = trim(value).toLowerCase(Locale.ROOT);
		if (!Set.of("mp3", "wav", "flac", "pcm").contains(audioFormat)) {
			throw new IllegalArgumentException("Unsupported MiniMax TTS audio format: " + value);
		}
		return audioFormat;
	}

	private static int supportedSampleRate(int value) {
		if (!Set.of(8_000, 16_000, 22_050, 24_000, 32_000, 44_100).contains(value)) {
			throw new IllegalArgumentException("Unsupported MiniMax TTS sample rate: " + value);
		}
		return value;
	}

	private static int supportedBitrate(int value) {
		if (!Set.of(32_000, 64_000, 128_000, 256_000).contains(value)) {
			throw new IllegalArgumentException("Unsupported MiniMax TTS bitrate: " + value);
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
		String text = trim(value);
		if (text.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
		return text;
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
