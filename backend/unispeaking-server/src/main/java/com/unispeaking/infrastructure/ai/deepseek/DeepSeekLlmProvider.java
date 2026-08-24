package com.unispeaking.infrastructure.ai.deepseek;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.LlmProvider;
import com.unispeaking.provider.AiProviderResponse;
import com.unispeaking.provider.ProviderUsage;
import com.unispeaking.provider.ProviderCredentialOverride;
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
public class DeepSeekLlmProvider extends LlmProvider {

	private static final int DEFAULT_MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final URI endpoint;
	private final String model;
	private final Duration readTimeout;
	private final int maxResponseBytes;

	@Autowired
	public DeepSeekLlmProvider(
			ObjectMapper objectMapper,
			@Value("${DEEPSEEK_API_KEY:}") String apiKey,
			@Value("${DEEPSEEK_LLM_ENDPOINT:https://api.deepseek.com/chat/completions}")
			String endpoint,
			@Value("${DEEPSEEK_LLM_MODEL:deepseek-v4-flash}") String model,
			@Value("${DEEPSEEK_LLM_CONNECT_TIMEOUT_SECONDS:10}") int connectTimeoutSeconds,
			@Value("${DEEPSEEK_LLM_READ_TIMEOUT_SECONDS:60}") int readTimeoutSeconds,
			@Value("${DEEPSEEK_LLM_MAX_RESPONSE_BYTES:2097152}") int maxResponseBytes) {
		this(
				HttpClient.newBuilder()
						.connectTimeout(positiveDuration(
								connectTimeoutSeconds,
								"DeepSeek LLM connect timeout"))
						.build(),
				objectMapper,
				apiKey,
				parseUri(endpoint),
				model,
				positiveDuration(readTimeoutSeconds, "DeepSeek LLM read timeout"),
				maxResponseBytes);
	}

	public DeepSeekLlmProvider(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			String apiKey,
			URI endpoint,
			String model,
			Duration readTimeout,
			int maxResponseBytes) {
		super("deepseek", Set.of(requiredText(model, "DeepSeek LLM model")));
		this.httpClient = require(httpClient, "DeepSeek LLM HTTP client");
		this.objectMapper = require(objectMapper, "DeepSeek LLM JSON mapper");
		this.apiKey = trim(apiKey);
		this.endpoint = endpoint;
		this.model = requiredText(model, "DeepSeek LLM model");
		this.readTimeout = requirePositive(readTimeout, "DeepSeek LLM read timeout");
		this.maxResponseBytes = maxResponseBytes > 0
				? maxResponseBytes
				: DEFAULT_MAX_RESPONSE_BYTES;
	}

	@Override
	public String executeLlmTask(String prompt, String token) {
		return executeLlmTaskMeasured(prompt, token).response();
	}

	@Override
	public AiProviderResponse<String> executeLlmTaskMeasured(String prompt, String token) {
		String credential = ProviderCredentialOverride.currentOr("apiKey", apiKey);
		if (credential.isBlank()) {
			throw retryableFailure(
					"DEEPSEEK_LLM_CREDENTIAL_MISSING",
					"Set DEEPSEEK_API_KEY before calling DeepSeek LLM");
		}
		return callForContent(prompt, credential);
	}

	private AiProviderResponse<String> callForContent(String promptValue, String credential) {
		String prompt = trim(promptValue);
		if (prompt.isBlank()) {
			throw nonRetryableFailure("INVALID_LLM_PROMPT", "LLM task prompt is required");
		}
		requireTrustedEndpoint();

		try {
			Map<String, Object> body = Map.of(
					"model", model,
					"messages", List.of(Map.of("role", "user", "content", prompt)),
					"thinking", Map.of("type", "disabled"),
					"stream", false);
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
							"DEEPSEEK_LLM_RESPONSE_TOO_LARGE",
							"DeepSeek LLM response exceeds the configured limit"));
			if (!successful(response.statusCode())) {
				throw retryableFailure(
						"DEEPSEEK_LLM_REQUEST_FAILED",
						"DeepSeek LLM returned HTTP " + response.statusCode());
			}
			JsonNode root = objectMapper.readTree(
					new String(response.body(), StandardCharsets.UTF_8));
			String content = root.path("choices")
					.path(0)
					.path("message")
					.path("content")
					.asString("");
			if (content.isBlank()) {
				throw retryableFailure(
						"DEEPSEEK_LLM_EMPTY_RESPONSE",
						"DeepSeek LLM returned no message content");
			}
			JsonNode usage = root.path("usage");
			long inputTokens = usage.path("prompt_tokens").longValue(usage.path("input_tokens").longValue(0));
			long outputTokens = usage.path("completion_tokens").longValue(usage.path("output_tokens").longValue(0));
			ProviderUsage measuredUsage = inputTokens > 0 || outputTokens > 0
					? new ProviderUsage(inputTokens, outputTokens, prompt.length(), content.length(), 0, 0, "PROVIDER")
					: ProviderUsage.estimatedText(prompt, content);
			return new AiProviderResponse<>(content, root.path("id").asString(null), measuredUsage);
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (JacksonException exception) {
			throw retryableFailure(
					"DEEPSEEK_LLM_RESPONSE_INVALID",
					"DeepSeek LLM response is not valid JSON");
		}
		catch (IOException exception) {
			BusinessException bodyError = businessCause(exception);
			if (bodyError != null) {
				throw bodyError;
			}
			throw retryableFailure(
					"DEEPSEEK_LLM_IO_ERROR",
					"Failed to call DeepSeek LLM");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw nonRetryableFailure(
					"DEEPSEEK_LLM_INTERRUPTED",
					"DeepSeek LLM call was interrupted");
		}
	}

	private Object parseContent(String content) {
		try {
			return objectMapper.readTree(content);
		}
		catch (Exception ignored) {
			return content;
		}
	}

	private void requireTrustedEndpoint() {
		String host = endpoint == null || endpoint.getHost() == null
				? ""
				: endpoint.getHost().toLowerCase(Locale.ROOT);
		if (endpoint == null
				|| !endpoint.isAbsolute()
				|| !"https".equalsIgnoreCase(endpoint.getScheme())
				|| !"api.deepseek.com".equals(host)
				|| endpoint.getUserInfo() != null
				|| endpoint.getPort() != -1
				|| !"/chat/completions".equals(endpoint.getPath())
				|| endpoint.getRawQuery() != null
				|| endpoint.getRawFragment() != null) {
			throw retryableFailure(
					"DEEPSEEK_LLM_ENDPOINT_INVALID",
					"DeepSeek LLM endpoint must be the trusted chat completions URL");
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
