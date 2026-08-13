package com.unispeaking.infrastructure.ai.qiniu;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.infrastructure.config.QiniuMaasProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class QiniuMaasLlmClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(QiniuMaasLlmClient.class);

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final QiniuMaasProperties properties;
	private final URI endpoint;

	public QiniuMaasLlmClient(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			QiniuMaasProperties properties) {
		this.httpClient = Objects.requireNonNull(httpClient, "Qiniu MaaS HTTP client is required");
		this.objectMapper = Objects.requireNonNull(objectMapper, "Qiniu MaaS JSON mapper is required");
		this.properties = Objects.requireNonNull(properties, "Qiniu MaaS properties are required");
		properties.validate();
		this.endpoint = properties.chatCompletionsUri();
	}

	QiniuMaasLlmClient(
			HttpClient httpClient,
			ObjectMapper objectMapper,
			QiniuMaasProperties properties,
			URI endpoint) {
		this.httpClient = Objects.requireNonNull(httpClient, "Qiniu MaaS HTTP client is required");
		this.objectMapper = Objects.requireNonNull(objectMapper, "Qiniu MaaS JSON mapper is required");
		this.properties = Objects.requireNonNull(properties, "Qiniu MaaS properties are required");
		properties.validate();
		this.endpoint = Objects.requireNonNull(endpoint, "Qiniu MaaS endpoint is required");
	}

	public String execute(String model, String promptValue) {
		String prompt = trim(promptValue);
		if (prompt.isBlank()) {
			throw new ProviderFailure("INVALID_LLM_PROMPT", "LLM task prompt is required", false);
		}
		if (properties.apiKey().isBlank()) {
			throw new ProviderFailure(
					"QINIU_MAAS_CREDENTIAL_MISSING",
					"Set QINIU_MAAS_API_KEY before calling Qiniu MaaS LLM",
					true);
		}

		long startedAt = System.nanoTime();
		LOGGER.info(
				"Qiniu MaaS LLM request started model={} promptChars={} maxOutputTokens={} timeoutMs={}",
				model,
				prompt.length(),
				properties.maxOutputTokens(),
				properties.readTimeout().toMillis());
		try {
			Map<String, Object> body = Map.of(
					"model", model,
					"messages", List.of(Map.of("role", "user", "content", prompt)),
					"stream", false,
					"max_tokens", properties.maxOutputTokens());
			HttpRequest request = HttpRequest.newBuilder()
					.uri(endpoint)
					.timeout(properties.readTimeout())
					.header("Authorization", "Bearer " + properties.apiKey())
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(
							objectMapper.writeValueAsString(body),
							StandardCharsets.UTF_8))
					.build();
			HttpResponse<InputStream> response = httpClient.send(
					request,
					HttpResponse.BodyHandlers.ofInputStream());
			byte[] responseBody;
			try (InputStream input = response.body()) {
				responseBody = input.readNBytes(properties.maxResponseBytes() + 1);
			}
			if (responseBody.length > properties.maxResponseBytes()) {
				throw failure(
						"QINIU_MAAS_LLM_RESPONSE_TOO_LARGE",
						"Qiniu MaaS LLM response exceeds the configured limit",
						true,
						model,
						startedAt);
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				boolean retryable = response.statusCode() != 401
						&& response.statusCode() != 403;
				throw failure(
						"QINIU_MAAS_LLM_REQUEST_FAILED",
						"Qiniu MaaS LLM returned HTTP " + response.statusCode(),
						retryable,
						model,
						startedAt);
			}
			JsonNode root = objectMapper.readTree(
					new String(responseBody, StandardCharsets.UTF_8));
			String content = root.path("choices")
					.path(0)
					.path("message")
					.path("content")
					.asString("");
			if (content.isBlank()) {
				throw failure(
						"QINIU_MAAS_LLM_EMPTY_RESPONSE",
						"Qiniu MaaS LLM returned no message content",
						true,
						model,
						startedAt);
			}
			LOGGER.info(
					"Qiniu MaaS LLM request completed model={} status={} durationMs={} responseChars={}",
					model,
					response.statusCode(),
					elapsedMillis(startedAt),
					content.length());
			return content;
		}
		catch (ProviderFailure exception) {
			throw exception;
		}
		catch (JacksonException exception) {
			throw failure(
					"QINIU_MAAS_LLM_RESPONSE_INVALID",
					"Qiniu MaaS LLM response is not valid JSON",
					true,
					model,
					startedAt);
		}
		catch (IOException exception) {
			throw failure(
					"QINIU_MAAS_LLM_IO_ERROR",
					"Failed to call Qiniu MaaS LLM",
					true,
					model,
					startedAt);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw failure(
					"QINIU_MAAS_LLM_INTERRUPTED",
					"Qiniu MaaS LLM call was interrupted",
					false,
					model,
					startedAt);
		}
	}

	private ProviderFailure failure(
			String code,
			String message,
			boolean retryable,
			String model,
			long startedAt) {
		LOGGER.warn(
				"Qiniu MaaS LLM request failed model={} durationMs={} errorCode={} retryable={}",
				model,
				elapsedMillis(startedAt),
				code,
				retryable);
		return new ProviderFailure(code, message, retryable);
	}

	private static long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	static final class ProviderFailure extends BusinessException {

		private final boolean retryable;

		ProviderFailure(String code, String message, boolean retryable) {
			super(code, message);
			this.retryable = retryable;
		}

		boolean retryable() {
			return retryable;
		}
	}
}
