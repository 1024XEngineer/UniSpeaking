package com.unispeaking.infrastructure.realtime;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.session.RealtimeCredential;
import com.unispeaking.infrastructure.config.RealtimeProperties;
import com.unispeaking.common.exception.BusinessException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.Duration;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Issues short-lived credentials for realtime provider connections.
 */
@Component
public class RealtimeCredentialIssuer {

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final RealtimeProperties qwenProperties;

	public RealtimeCredentialIssuer(
			HttpClient realtimeHttpClient,
			ObjectMapper objectMapper,
			RealtimeProperties qwenProperties) {
		this.httpClient = realtimeHttpClient;
		this.objectMapper = objectMapper;
		this.qwenProperties = qwenProperties;
	}

	public RealtimeCredential issue(ProviderType providerType) {
		if (providerType != ProviderType.QWEN) {
			throw new BusinessException(
					"REALTIME_CREDENTIAL_PROVIDER_MISSING",
					"No credential provider is configured for " + providerType);
		}
		return issueQwenTemporaryKey();
	}

	private RealtimeCredential issueQwenTemporaryKey() {
		String parentApiKey = parentApiKey();
		if (parentApiKey.isBlank()) {
			throw new BusinessException(
					"QWEN_CREDENTIAL_MISSING",
					"Set DASHSCOPE_API_KEY in deploy/env/.env before starting a realtime session");
		}

		URI uri = URI.create(qwenProperties.getTemporaryKeyEndpoint()
				+ "?expire_in_seconds=" + qwenProperties.getTemporaryKeyTtlSeconds());
		RealtimeFlowLog.info("flow.2.token.request endpoint={} ttlSeconds={} parentApiKey={}",
				qwenProperties.getTemporaryKeyEndpoint(), qwenProperties.getTemporaryKeyTtlSeconds(),
				RealtimeFlowLog.maskSecret(parentApiKey));
		HttpRequest request = HttpRequest.newBuilder()
				.uri(uri)
				.timeout(qwenProperties.getReadTimeout())
				.header("Authorization", "Bearer " + parentApiKey)
				.POST(HttpRequest.BodyPublishers.noBody())
				.build();
		Instant issuedAt = Instant.now();
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			JsonNode payload = parsePayload(response.body());
			if (response.statusCode() >= 400) {
				String code = text(payload, "code", "TemporaryKeyRequestFailed");
				String message = text(payload, "message", "temporary key request failed");
				throw new BusinessException("QWEN_TEMPORARY_KEY_FAILED", code + ": " + message);
			}
			String token = text(payload, "token", "");
			if (token.isBlank()) {
				throw new BusinessException("QWEN_TEMPORARY_KEY_INVALID", "temporary key response is missing token");
			}
			Instant expiresAt = expiresAt(payload, issuedAt);
			RealtimeFlowLog.info("flow.2.token.response status={} token={} expiresAt={} ttlRemainingSeconds={} expiresAtFromResponse={}",
					response.statusCode(), RealtimeFlowLog.maskSecret(token), expiresAt,
					Duration.between(Instant.now(), expiresAt).toSeconds(),
					firstPositiveLong(payload, "expires_at", "expiresAt", "expire_at") > 0);
			return new RealtimeCredential(token, expiresAt);
		}
		catch (IOException exception) {
			throw new BusinessException("QWEN_TEMPORARY_KEY_IO_ERROR", "Failed to request Qwen temporary key");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new BusinessException("QWEN_TEMPORARY_KEY_INTERRUPTED", "Qwen temporary key request was interrupted");
		}
	}

	private String parentApiKey() {
		return qwenProperties.getApiKey();
	}

	private JsonNode parsePayload(String body) {
		try {
			return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
		}
		catch (JacksonException exception) {
			throw new BusinessException("QWEN_TEMPORARY_KEY_INVALID", "temporary key response is not valid JSON");
		}
	}

	private String text(JsonNode payload, String field, String fallback) {
		JsonNode value = payload.path(field);
		return value.isTextual() ? value.asString() : fallback;
	}

	private Instant expiresAt(JsonNode payload, Instant issuedAt) {
		long unixSeconds = firstPositiveLong(payload, "expires_at", "expiresAt", "expire_at");
		if (unixSeconds > 0) {
			return Instant.ofEpochSecond(unixSeconds);
		}
		return issuedAt.plusSeconds(qwenProperties.getTemporaryKeyTtlSeconds());
	}

	private long firstPositiveLong(JsonNode payload, String... fields) {
		for (String field : fields) {
			long value = payload.path(field).longValue(0);
			if (value > 0) {
				return value;
			}
		}
		return 0;
	}
}
