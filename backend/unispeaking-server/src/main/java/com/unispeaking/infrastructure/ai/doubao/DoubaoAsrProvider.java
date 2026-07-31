package com.unispeaking.infrastructure.ai.doubao;

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
import java.util.UUID;
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
public class DoubaoAsrProvider extends TranscriptionProvider {

	private static final String SUCCESS_STATUS = "20000000";
	private static final int DEFAULT_MAX_AUDIO_BYTES = 20 * 1024 * 1024;
	private static final int DEFAULT_MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final String appKey;
	private final String accessKey;
	private final String userId;
	private final URI endpoint;
	private final String resourceId;
	private final Duration readTimeout;
	private final int maxAudioBytes;
	private final int maxResponseBytes;

	@Autowired
	public DoubaoAsrProvider(
			ObjectMapper objectMapper,
			@Value("${DOUBAO_ASR_API_KEY:}") String apiKey,
			@Value("${DOUBAO_ASR_APP_KEY:}") String appKey,
			@Value("${DOUBAO_ASR_ACCESS_KEY:}") String accessKey,
			@Value("${DOUBAO_ASR_USER_ID:unispeaking}") String userId,
			@Value("${DOUBAO_ASR_ENDPOINT:https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash}")
			String endpoint,
			@Value("${DOUBAO_ASR_RESOURCE_ID:volc.bigasr.auc_turbo}") String resourceId,
			@Value("${DOUBAO_ASR_CONNECT_TIMEOUT_SECONDS:10}") int connectTimeoutSeconds,
			@Value("${DOUBAO_ASR_READ_TIMEOUT_SECONDS:120}") int readTimeoutSeconds,
			@Value("${DOUBAO_ASR_MAX_AUDIO_BYTES:20971520}") int maxAudioBytes,
			@Value("${DOUBAO_ASR_MAX_RESPONSE_BYTES:4194304}") int maxResponseBytes) {
		this(
				HttpClient.newBuilder()
						.connectTimeout(positiveDuration(
								connectTimeoutSeconds,
								"Doubao ASR connect timeout"))
						.build(),
				objectMapper,
				apiKey,
				appKey,
				accessKey,
				userId,
				parseUri(endpoint),
				resourceId,
				positiveDuration(readTimeoutSeconds, "Doubao ASR read timeout"),
				maxAudioBytes,
				maxResponseBytes);
	}

	public DoubaoAsrProvider(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			String apiKey,
			String appKey,
			String accessKey,
			String userId,
			URI endpoint,
			String resourceId,
			Duration readTimeout,
			int maxAudioBytes,
			int maxResponseBytes) {
		super("doubao", Set.of(requiredText(resourceId, "Doubao ASR resource ID")));
		this.httpClient = require(httpClient, "Doubao ASR HTTP client");
		this.objectMapper = require(objectMapper, "Doubao ASR JSON mapper");
		this.apiKey = trim(apiKey);
		this.appKey = trim(appKey);
		this.accessKey = trim(accessKey);
		this.userId = requiredText(userId, "Doubao ASR user ID");
		this.endpoint = endpoint;
		this.resourceId = requiredText(resourceId, "Doubao ASR resource ID");
		this.readTimeout = requirePositive(readTimeout, "Doubao ASR read timeout");
		this.maxAudioBytes = maxAudioBytes > 0 ? maxAudioBytes : DEFAULT_MAX_AUDIO_BYTES;
		this.maxResponseBytes = maxResponseBytes > 0
				? maxResponseBytes
				: DEFAULT_MAX_RESPONSE_BYTES;
	}

	@Override
	public String convertAudioToText(Byte[] audio, String token) {
		AudioInput input = new AudioInput(
				unboxAudio(audio, "Doubao ASR"),
				"wav");
		requireAudio(input);
		requireCredentials();
		return transcribe(input);
	}

	private String transcribe(AudioInput audio) {
		requireTrustedEndpoint();

		try {
			Map<String, Object> body = Map.of(
					"user", Map.of("uid", userId),
					"audio", Map.of(
							"data",
							Base64.getEncoder().encodeToString(audio.audioData())),
					"request", Map.of("model_name", "bigmodel"));
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
					.uri(endpoint)
					.timeout(readTimeout)
					.header("Content-Type", "application/json")
					.header("X-Api-Resource-Id", resourceId)
					.header("X-Api-Request-Id", UUID.randomUUID().toString())
					.header("X-Api-Sequence", "-1");
			if (!apiKey.isBlank()) {
				requestBuilder.header("X-Api-Key", apiKey);
			}
			else {
				requestBuilder
						.header("X-Api-App-Key", appKey)
						.header("X-Api-Access-Key", accessKey);
			}
			HttpResponse<byte[]> response = httpClient.send(
					requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
							objectMapper.writeValueAsString(body),
							StandardCharsets.UTF_8)).build(),
					limitedBodyHandler(
							maxResponseBytes,
							"DOUBAO_ASR_RESPONSE_TOO_LARGE",
							"Doubao ASR response exceeds the configured limit"));
			if (!successful(response.statusCode())) {
				throw retryableFailure(
						"DOUBAO_ASR_REQUEST_FAILED",
						"Doubao ASR returned HTTP " + response.statusCode());
			}
			String providerStatus = response.headers()
					.firstValue("X-Api-Status-Code")
					.orElse("");
			if (!SUCCESS_STATUS.equals(providerStatus)) {
				throw retryableFailure(
						"DOUBAO_ASR_REQUEST_FAILED",
						"Doubao ASR returned provider status "
								+ (providerStatus.isBlank() ? "missing" : providerStatus));
			}
			JsonNode root = objectMapper.readTree(
					new String(response.body(), StandardCharsets.UTF_8));
			String text = root.path("result").path("text").asString("");
			if (text.isBlank()) {
				throw retryableFailure(
						"DOUBAO_ASR_RESULT_EMPTY",
						"Doubao ASR returned no transcription text");
			}
			return text;
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (JacksonException exception) {
			throw retryableFailure(
					"DOUBAO_ASR_RESPONSE_INVALID",
					"Doubao ASR response is not valid JSON");
		}
		catch (IOException exception) {
			BusinessException bodyError = businessCause(exception);
			if (bodyError != null) {
				throw bodyError;
			}
			throw retryableFailure(
					"DOUBAO_ASR_IO_ERROR",
					"Failed to call Doubao ASR");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw nonRetryableFailure(
					"DOUBAO_ASR_INTERRUPTED",
					"Doubao ASR call was interrupted");
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
					"Doubao ASR audio exceeds the configured limit");
		}
		requireSupportedFormat(audio.audioFormat());
		return audio;
	}

	private void requireSupportedFormat(String audioFormat) {
		String format = trim(audioFormat).toLowerCase(Locale.ROOT);
		if (!Set.of("wav", "mp3", "ogg", "opus").contains(format)) {
			throw nonRetryableFailure(
					"UNSUPPORTED_TRANSCRIPTION_AUDIO_FORMAT",
					"Unsupported Doubao ASR audio format: " + audioFormat);
		}
	}

	private void requireCredentials() {
		boolean newCredential = !apiKey.isBlank();
		boolean legacyCredential = !appKey.isBlank() && !accessKey.isBlank();
		if (!newCredential && !legacyCredential) {
			throw retryableFailure(
					"DOUBAO_ASR_CREDENTIAL_MISSING",
					"Set DOUBAO_ASR_API_KEY or both legacy Doubao ASR keys");
		}
	}

	private void requireTrustedEndpoint() {
		String host = endpoint == null || endpoint.getHost() == null
				? ""
				: endpoint.getHost().toLowerCase(Locale.ROOT);
		if (endpoint == null
				|| !endpoint.isAbsolute()
				|| !"https".equalsIgnoreCase(endpoint.getScheme())
				|| !"openspeech.bytedance.com".equals(host)
				|| endpoint.getUserInfo() != null
				|| endpoint.getPort() != -1
				|| !"/api/v3/auc/bigmodel/recognize/flash".equals(endpoint.getPath())
				|| endpoint.getRawQuery() != null
				|| endpoint.getRawFragment() != null) {
			throw retryableFailure(
					"DOUBAO_ASR_ENDPOINT_INVALID",
					"Doubao ASR endpoint must be the trusted BigASR flash URL");
		}
	}

	private static URI parseUri(String value) {
		try {
			return URI.create(trim(value));
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
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
