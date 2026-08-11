package com.unispeaking.infrastructure.ai.qiniu;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.infrastructure.config.QiniuRealtimeProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class QiniuRtiClient {

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final QiniuRealtimeProperties properties;

	public QiniuRtiClient(
			@Qualifier("qiniuRealtimeHttpClient") HttpClient realtimeHttpClient,
			ObjectMapper objectMapper,
			QiniuRealtimeProperties properties) {
		this.httpClient = realtimeHttpClient;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	public CreatedSession createSession(
			RealtimeConnectCommand command,
			String voiceProfile) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("app_id", properties.appId());
		payload.put("user_id", command.userId());
		payload.put("client_id", command.localSessionId());
		payload.put("model_profile", properties.modelProfile());
		payload.put("role_profile", properties.roleProfile());
		payload.put("voice_profile", voiceProfile);
		payload.put("client_transport", "platform_rtc");
		if (!properties.scenario().isBlank()) {
			payload.put("scenario", properties.scenario());
		}

		JsonNode response = sendControlRequest(
				"POST",
				"/rtic/v1/realtime/sessions",
				payload,
				false);
		String sessionId = requiredText(response, "session_id");
		JsonNode endpoint = response.path("client_endpoint");
		String endpointType = requiredText(endpoint, "type");
		if (!"platform_rtc".equals(endpointType)) {
			throw new BusinessException(
					"QINIU_RTI_RESPONSE_INVALID",
					"Qiniu RTI session did not return a platform_rtc endpoint");
		}
		URI endpointUri = resolveEndpoint(requiredText(endpoint, "url"));
		String accessToken = requiredText(endpoint, "access_token");
		long expiresAtMillis = endpoint.path("expires_at_ms").longValue(0);
		Instant expiresAt = expiresAtMillis > 0
				? Instant.ofEpochMilli(expiresAtMillis)
				: null;
		return new CreatedSession(
				sessionId,
				text(response, "trace_id"),
				endpointUri,
				accessToken,
				expiresAt);
	}

	public String exchangeSdp(CreatedSession session, String offerSdp) {
		Map<String, String> payload = Map.of("type", "offer", "sdp", offerSdp);
		HttpRequest request = jsonRequest(
				session.rtcEndpoint(),
				session.accessToken(),
				payload);
		JsonNode response = send(request, "QINIU_RTC_SIGNALING_FAILED", true);
		String answerType = requiredText(response, "type");
		if (!"answer".equals(answerType)) {
			throw new BusinessException(
					"QINIU_RTI_RESPONSE_INVALID",
					"Qiniu RTC signaling response is missing an answer");
		}
		return requiredText(response, "sdp");
	}

	public void stopSession(String providerSessionId, String reason) {
		if (providerSessionId == null
				|| !providerSessionId.matches("[A-Za-z0-9._:-]+")) {
			throw new BusinessException(
					"QINIU_SESSION_ID_INVALID",
					"Qiniu RTI session ID is invalid");
		}
		String encodedSessionId = URLEncoder.encode(
				providerSessionId,
				StandardCharsets.UTF_8);
		sendControlRequest(
				"POST",
				"/rtic/v1/realtime/sessions/" + encodedSessionId + "/stop",
				Map.of("reason", reason),
				true);
	}

	private JsonNode sendControlRequest(
			String method,
			String path,
			Object payload,
			boolean acceptEmptyResponse) {
		HttpRequest request = jsonRequest(
				properties.baseUri().resolve(path),
				properties.apiKey(),
				payload,
				method);
		return send(
				request,
				"QINIU_RTI_CONTROL_REQUEST_FAILED",
				acceptEmptyResponse);
	}

	private HttpRequest jsonRequest(URI uri, String bearerToken, Object payload) {
		return jsonRequest(uri, bearerToken, payload, "POST");
	}

	private HttpRequest jsonRequest(
			URI uri,
			String bearerToken,
			Object payload,
			String method) {
		String json;
		try {
			json = objectMapper.writeValueAsString(payload);
		}
		catch (JacksonException exception) {
			throw new BusinessException(
					"QINIU_RTI_REQUEST_INVALID",
					"Failed to encode the Qiniu RTI request");
		}
		return HttpRequest.newBuilder()
				.uri(uri)
				.timeout(properties.readTimeout())
				.header("Authorization", "Bearer " + bearerToken)
				.header("Content-Type", "application/json")
				.header("X-Request-ID", UUID.randomUUID().toString())
				.method(method, HttpRequest.BodyPublishers.ofString(json))
				.build();
	}

	private JsonNode send(
			HttpRequest request,
			String failureCode,
			boolean acceptEmptyResponse) {
		try {
			HttpResponse<String> response = httpClient.send(
					request,
					HttpResponse.BodyHandlers.ofString());
			String body = response.body() == null ? "" : response.body();
			if (body.getBytes(StandardCharsets.UTF_8).length > properties.maxResponseBytes()) {
				throw new BusinessException(
						"QINIU_RTI_RESPONSE_TOO_LARGE",
						"Qiniu RTI response exceeds the configured limit");
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				String code = response.statusCode() == 400
						? "QINIU_RTI_INVALID_REQUEST"
						: failureCode;
				throw new BusinessException(
						code,
						"Qiniu RTI returned HTTP " + response.statusCode());
			}
			if (body.isBlank() && acceptEmptyResponse) {
				return objectMapper.createObjectNode();
			}
			return parse(body);
		}
		catch (IOException exception) {
			throw new BusinessException(failureCode, "Failed to call Qiniu RTI");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new BusinessException(
					"QINIU_RTI_REQUEST_INTERRUPTED",
					"Qiniu RTI request was interrupted");
		}
	}

	private JsonNode parse(String body) {
		try {
			return objectMapper.readTree(body);
		}
		catch (JacksonException exception) {
			throw new BusinessException(
					"QINIU_RTI_RESPONSE_INVALID",
					"Qiniu RTI response is not valid JSON");
		}
	}

	private URI resolveEndpoint(String endpointUrl) {
		URI endpoint = properties.baseUri().resolve(endpointUrl);
		String baseScheme = properties.baseUri().getScheme();
		if (!endpoint.isAbsolute()
				|| endpoint.getHost() == null
				|| endpoint.getUserInfo() != null
				|| !baseScheme.equalsIgnoreCase(endpoint.getScheme())) {
			throw new BusinessException(
					"QINIU_RTI_ENDPOINT_INVALID",
					"Qiniu RTI returned an invalid RTC endpoint");
		}
		return endpoint;
	}

	private String requiredText(JsonNode node, String field) {
		String value = text(node, field);
		if (value.isBlank()) {
			throw new BusinessException(
					"QINIU_RTI_RESPONSE_INVALID",
					"Qiniu RTI response is missing " + field);
		}
		return value;
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isTextual() ? value.asString() : "";
	}

	public record CreatedSession(
			String sessionId,
			String traceId,
			URI rtcEndpoint,
			String accessToken,
			Instant expiresAt) {
	}
}
