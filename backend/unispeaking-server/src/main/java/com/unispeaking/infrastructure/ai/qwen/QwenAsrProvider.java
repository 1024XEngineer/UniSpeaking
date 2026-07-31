package com.unispeaking.infrastructure.ai.qwen;

import com.unispeaking.domain.vo.evaluation.AudioInput;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.TranscriptionProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
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
public class QwenAsrProvider extends TranscriptionProvider {

	private static final int DEFAULT_MAX_AUDIO_BYTES = 7 * 1024 * 1024;
	private static final int DEFAULT_MAX_RESPONSE_BYTES = 1024 * 1024;

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final URI endpoint;
	private final String model;
	private final Duration readTimeout;
	private final int maxAudioBytes;
	private final int maxResponseBytes;

	@Autowired
	public QwenAsrProvider(
			ObjectMapper objectMapper,
			@Value("${DASHSCOPE_API_KEY:}") String apiKey,
			@Value("${BAILIAN_WORKSPACE_ID:}") String workspaceId,
			@Value("${BAILIAN_REGION:cn-beijing}") String region,
			@Value("${QWEN_ASR_MODEL:qwen3-asr-flash}") String model,
			@Value("${QWEN_ASR_CONNECT_TIMEOUT_SECONDS:10}") int connectTimeoutSeconds,
			@Value("${QWEN_ASR_READ_TIMEOUT_SECONDS:60}") int readTimeoutSeconds,
			@Value("${QWEN_ASR_MAX_AUDIO_BYTES:7340032}") int maxAudioBytes,
			@Value("${QWEN_ASR_MAX_RESPONSE_BYTES:1048576}") int maxResponseBytes) {
		this(
				HttpClient.newBuilder()
						.connectTimeout(positiveDuration(
								connectTimeoutSeconds,
								"Qwen ASR connect timeout"))
						.build(),
				objectMapper,
				apiKey,
				buildEndpoint(workspaceId, region),
				model,
				positiveDuration(readTimeoutSeconds, "Qwen ASR read timeout"),
				maxAudioBytes,
				maxResponseBytes);
	}

	public QwenAsrProvider(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			String apiKey,
			URI endpoint,
			String model,
			Duration readTimeout,
			int maxAudioBytes,
			int maxResponseBytes) {
		super("qwen", Set.of(requiredText(model, "Qwen ASR model")));
		this.httpClient = require(httpClient, "Qwen ASR HTTP client");
		this.objectMapper = require(objectMapper, "Qwen ASR JSON mapper");
		this.apiKey = trim(apiKey);
		this.endpoint = endpoint;
		this.model = requiredText(model, "Qwen ASR model");
		this.readTimeout = requirePositive(readTimeout, "Qwen ASR read timeout");
		this.maxAudioBytes = maxAudioBytes > 0 ? maxAudioBytes : DEFAULT_MAX_AUDIO_BYTES;
		this.maxResponseBytes = maxResponseBytes > 0
				? maxResponseBytes
				: DEFAULT_MAX_RESPONSE_BYTES;
	}

	@Override
	public String convertAudioToText(Byte[] audio, String token) {
		AudioInput input = new AudioInput(
				unboxAudio(audio, "Qwen ASR"),
				"wav");
		requireAudio(input);
		if (apiKey.isBlank()) {
			throw retryableFailure(
					"QWEN_ASR_CREDENTIAL_MISSING",
					"Set DASHSCOPE_API_KEY before calling Qwen ASR");
		}
		return transcribe(input, apiKey);
	}

	private String transcribe(AudioInput audio, String credential) {
		requireTrustedEndpoint();

		try {
			String dataUrl = "data:" + mediaType(audio.audioFormat()) + ";base64,"
					+ Base64.getEncoder().encodeToString(audio.audioData());
			Map<String, Object> audioContent = Map.of(
					"type", "input_audio",
					"input_audio", Map.of("data", dataUrl));
			Map<String, Object> body = Map.of(
					"model", model,
					"messages", List.of(Map.of(
							"role", "user",
							"content", List.of(audioContent))),
					"stream", false,
					"asr_options", Map.of("enable_itn", true));
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
							maxResponseBytes,
							"QWEN_ASR_RESPONSE_TOO_LARGE",
							"Qwen ASR response exceeds the configured limit"));
			if (!successful(response.statusCode())) {
				throw retryableFailure(
						"QWEN_ASR_REQUEST_FAILED",
						"Qwen ASR returned HTTP " + response.statusCode());
			}
			JsonNode root = objectMapper.readTree(
					new String(response.body(), StandardCharsets.UTF_8));
			String text = root.path("choices")
					.path(0)
					.path("message")
					.path("content")
					.asString("");
			if (text.isBlank()) {
				throw retryableFailure(
						"QWEN_ASR_RESULT_EMPTY",
						"Qwen ASR returned no transcription text");
			}
			return text;
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (JacksonException exception) {
			throw retryableFailure(
					"QWEN_ASR_RESPONSE_INVALID",
					"Qwen ASR response is not valid JSON");
		}
		catch (IOException exception) {
			BusinessException bodyError = businessCause(exception);
			if (bodyError != null) {
				throw bodyError;
			}
			throw retryableFailure(
					"QWEN_ASR_IO_ERROR",
					"Failed to call Qwen ASR");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw nonRetryableFailure(
					"QWEN_ASR_INTERRUPTED",
					"Qwen ASR call was interrupted");
		}
	}

	private AudioInput requireAudio(AudioInput audio) {
		if (audio == null || audio.audioData() == null || audio.audioData().length == 0) {
			throw nonRetryableFailure(
					"INVALID_TRANSCRIPTION_AUDIO",
					"Audio transcription data is required");
		}
		if (audio.audioData().length > maxAudioBytes) {
			throw nonRetryableFailure(
					"TRANSCRIPTION_AUDIO_TOO_LARGE",
					"Qwen ASR audio exceeds the configured limit");
		}
		mediaType(audio.audioFormat());
		return audio;
	}

	private String mediaType(String audioFormat) {
		return switch (trim(audioFormat).toLowerCase(Locale.ROOT)) {
			case "wav" -> "audio/wav";
			case "mp3" -> "audio/mpeg";
			case "aac" -> "audio/aac";
			case "m4a" -> "audio/mp4";
			case "flac" -> "audio/flac";
			case "ogg", "opus" -> "audio/ogg";
			default -> throw nonRetryableFailure(
					"UNSUPPORTED_TRANSCRIPTION_AUDIO_FORMAT",
					"Unsupported Qwen ASR audio format: " + audioFormat);
		};
	}

	private void requireTrustedEndpoint() {
		String host = endpoint == null || endpoint.getHost() == null
				? ""
				: endpoint.getHost().toLowerCase(Locale.ROOT);
		if (endpoint == null
				|| !endpoint.isAbsolute()
				|| !"https".equalsIgnoreCase(endpoint.getScheme())
				|| !host.endsWith(".maas.aliyuncs.com")
				|| endpoint.getUserInfo() != null
				|| endpoint.getPort() != -1
				|| !"/compatible-mode/v1/chat/completions".equals(endpoint.getPath())
				|| endpoint.getRawQuery() != null
				|| endpoint.getRawFragment() != null) {
			throw retryableFailure(
					"QWEN_ASR_ENDPOINT_INVALID",
					"Qwen ASR endpoint must be the trusted Aliyun compatible-mode URL");
		}
	}

	private static URI buildEndpoint(String workspaceId, String region) {
		String workspace = trim(workspaceId);
		String endpointRegion = trim(region);
		if (!safeEndpointComponent(workspace) || !safeEndpointComponent(endpointRegion)) {
			return null;
		}
		return URI.create("https://" + workspace + "." + endpointRegion
				+ ".maas.aliyuncs.com/compatible-mode/v1/chat/completions");
	}

	private static boolean safeEndpointComponent(String value) {
		return !value.isBlank() && value.matches("[A-Za-z0-9-]+");
	}

	private static boolean successful(int statusCode) {
		return statusCode >= 200 && statusCode < 300;
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
