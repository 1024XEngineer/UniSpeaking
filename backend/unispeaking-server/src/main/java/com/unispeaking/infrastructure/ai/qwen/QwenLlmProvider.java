package com.unispeaking.infrastructure.ai.qwen;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.LlmProvider;
import com.unispeaking.provider.LlmResponseFormat;
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
import java.util.LinkedHashMap;
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
public class QwenLlmProvider extends LlmProvider {

	private static final int DEFAULT_MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final String apiKey;
	private final URI endpoint;
	private final String model;
	private final Duration readTimeout;
	private final int maxResponseBytes;

	@Autowired
	public QwenLlmProvider(
			ObjectMapper objectMapper,
			@Value("${DASHSCOPE_API_KEY:}") String apiKey,
			@Value("${BAILIAN_WORKSPACE_ID:}") String workspaceId,
			@Value("${BAILIAN_REGION:cn-beijing}") String region,
			@Value("${QWEN_LLM_MODEL:qwen3.5-plus}") String model,
			@Value("${QWEN_LLM_CONNECT_TIMEOUT_SECONDS:10}") int connectTimeoutSeconds,
			@Value("${QWEN_LLM_READ_TIMEOUT_SECONDS:60}") int readTimeoutSeconds,
			@Value("${QWEN_LLM_MAX_RESPONSE_BYTES:2097152}") int maxResponseBytes) {
		this(
				HttpClient.newBuilder()
						.connectTimeout(positiveDuration(connectTimeoutSeconds, "Qwen LLM connect timeout"))
						.build(),
				objectMapper,
				apiKey,
				buildEndpoint(workspaceId, region),
				model,
				positiveDuration(readTimeoutSeconds, "Qwen LLM read timeout"),
				maxResponseBytes);
	}

	public QwenLlmProvider(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			String apiKey,
			URI endpoint,
			String model,
			Duration readTimeout,
			int maxResponseBytes) {
		super("qwen", Set.of(modelOrDefault(model)));
		this.httpClient = require(httpClient, "Qwen LLM HTTP client");
		this.objectMapper = require(objectMapper, "Qwen LLM JSON mapper");
		this.apiKey = trim(apiKey);
		this.endpoint = endpoint;
		this.model = modelOrDefault(model);
		this.readTimeout = requirePositive(readTimeout, "Qwen LLM read timeout");
		this.maxResponseBytes = maxResponseBytes > 0
				? maxResponseBytes
				: DEFAULT_MAX_RESPONSE_BYTES;
	}

	@Override
	public String executeLlmTask(String prompt, String token) {
		return executeLlmTask(prompt, token, LlmResponseFormat.TEXT);
	}

	@Override
	public String executeLlmTask(
			String prompt,
			String token,
			LlmResponseFormat responseFormat) {
		if (apiKey.isBlank()) {
			throw retryableFailure(
					"QWEN_LLM_CREDENTIAL_MISSING",
					"Set DASHSCOPE_API_KEY before calling Qwen LLM");
		}
		return callForContent(prompt, apiKey, responseFormat);
	}

	private String callForContent(
			String promptValue,
			String credential,
			LlmResponseFormat responseFormat) {
		String prompt = trim(promptValue);
		if (prompt.isBlank()) {
			throw nonRetryableFailure("INVALID_LLM_PROMPT", "LLM task prompt is required");
		}
		requireHttpsEndpoint();

		try {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("model", model);
			body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
			body.put("enable_thinking", false);
			if (responseFormat == LlmResponseFormat.JSON_OBJECT) {
				body.put("response_format", Map.of("type", "json_object"));
			}
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
							"QWEN_LLM_RESPONSE_TOO_LARGE",
							"Qwen LLM response exceeds the configured limit"));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw retryableFailure(
						"QWEN_LLM_REQUEST_FAILED",
						"Qwen LLM returned HTTP " + response.statusCode());
			}
			String responseBody = new String(response.body(), StandardCharsets.UTF_8);
			JsonNode root = objectMapper.readTree(responseBody);
			String content = root.path("choices")
					.path(0)
					.path("message")
					.path("content")
					.asString("");
			if (content.isBlank()) {
				throw retryableFailure(
						"QWEN_LLM_EMPTY_RESPONSE",
						"Qwen LLM returned no message content");
			}
			return content;
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (JacksonException exception) {
			throw retryableFailure(
					"QWEN_LLM_RESPONSE_INVALID",
					"Qwen LLM response is not valid JSON");
		}
		catch (IOException exception) {
			BusinessException bodyError = businessCause(exception);
			if (bodyError != null) {
				throw bodyError;
			}
			throw retryableFailure(
					"QWEN_LLM_IO_ERROR",
					"Failed to call Qwen LLM");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw nonRetryableFailure(
					"QWEN_LLM_INTERRUPTED",
					"Qwen LLM call was interrupted");
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

	private void requireHttpsEndpoint() {
		String host = endpoint == null || endpoint.getHost() == null
				? ""
				: endpoint.getHost().toLowerCase(java.util.Locale.ROOT);
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
					"QWEN_LLM_ENDPOINT_INVALID",
					"Qwen LLM endpoint must be the trusted Aliyun compatible-mode URL");
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

	private static <T> T require(T value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " is required");
		}
		return value;
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static String modelOrDefault(String model) {
		String configured = trim(model);
		return configured.isBlank() ? AiProviderRegistry.QWEN_LLM_PLUS : configured;
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
