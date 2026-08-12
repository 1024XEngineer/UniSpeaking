package com.unispeaking.infrastructure.ai.qiniu;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.domain.vo.session.RealtimeCredential;
import com.unispeaking.infrastructure.config.QiniuRealtimeProperties;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.RealtimeProvider;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class QiniuRealtimeProvider extends RealtimeProvider {

	private static final String PROFILES_PATH = "/rtic/v1/realtime/profiles";
	private static final String SESSIONS_PATH = "/rtic/v1/realtime/sessions";

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;
	private final QiniuRealtimeProperties properties;

	public QiniuRealtimeProvider(
			HttpClient realtimeHttpClient,
			ObjectMapper objectMapper,
			QiniuRealtimeProperties properties) {
		super(ProviderType.QINIU, Set.of(AiProviderRegistry.QINIU_REALTIME_PLUS));
		this.httpClient = realtimeHttpClient;
		this.objectMapper = objectMapper;
		this.properties = properties;
	}

	@Override
	public boolean requiresIssuedCredential() {
		return false;
	}

	@Override
	public String exchangeRealtimeSdp(String modelId, String offerSdp, String token) {
		throw nonRetryableFailure(
				"QINIU_REALTIME_CONTEXT_REQUIRED",
				"Qiniu realtime requires a control-plane session before SDP exchange");
	}

	@Override
	public RealtimeConnectionResult connect(
			RealtimeConnectCommand command,
			RealtimeCredential ignoredCredential) {
		validateCommand(command);
		String requestId = "req_unispeaking_" + UUID.randomUUID().toString().replace("-", "");
		validateProfile(requestId);
		CreatedSession session = createSession(command, requestId);
		try {
			String answerSdp = exchangeSessionSdp(session, command.offerSdp(), requestId);
			return new RealtimeConnectionResult(
					session.sessionId(),
					type(),
					properties.modelProfile(),
					properties.voiceProfile(),
					session.traceId(),
					answerSdp,
					session.expiresAt());
		}
		catch (RuntimeException exception) {
			stopQuietly(session.sessionId(), "signaling_failed");
			throw exception;
		}
	}

	@Override
	public void stopSession(String providerSessionId, String ignoredToken, String reason) {
		if (providerSessionId == null || providerSessionId.isBlank()) return;
		String normalizedSessionId = requireSafeSessionId(providerSessionId);
		String payload = writeJson(objectMapper.createObjectNode()
				.put("reason", normalizedReason(reason)));
		HttpResponse<String> response = send(
				controlRequest(
						properties.controlUri(SESSIONS_PATH + "/" + normalizedSessionId + "/stop"),
						"req_unispeaking_stop_" + UUID.randomUUID().toString().replace("-", ""))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(payload))
					.build(),
				"QINIU_SESSION_STOP_IO_ERROR");
		if (response.statusCode() == 404) return;
		requireSuccess(response, "QINIU_SESSION_STOP_FAILED", true);
		RealtimeFlowLog.info(
				"realtime.provider.stop provider={} providerSessionId={} status={}",
				type(),
				providerSessionId,
				response.statusCode());
	}

	private void validateCommand(RealtimeConnectCommand command) {
		if (command == null) {
			throw nonRetryableFailure("INVALID_REALTIME_COMMAND", "Realtime connect command is required");
		}
		if (command.offerSdp() == null || command.offerSdp().isBlank()) {
			throw nonRetryableFailure("INVALID_SDP", "WebRTC offer SDP is required");
		}
		if (properties.apiKey().isBlank()) {
			throw retryableFailure(
					"QINIU_CREDENTIAL_MISSING",
					"Set QINIU_RTI_API_KEY before starting a Qiniu realtime session");
		}
		if (command.modelId() != null && !command.modelId().isBlank()
				&& !supports(command.modelId())) {
			throw nonRetryableFailure(
					"QINIU_REALTIME_MODEL_NOT_SUPPORTED",
					"Qiniu realtime model is not registered: " + command.modelId());
		}
	}

	private void validateProfile(String requestId) {
		HttpResponse<String> response = send(
				controlRequest(properties.controlUri(PROFILES_PATH), requestId)
					.GET()
					.build(),
				"QINIU_PROFILES_IO_ERROR");
		requireSuccess(response, "QINIU_PROFILES_FAILED", true);
		JsonNode root = parseJson(response.body(), "QINIU_PROFILES_INVALID");
		for (JsonNode profile : root.path("profiles")) {
			if (properties.modelProfile().equals(text(profile, "model_profile"))
					&& contains(profile.path("role_profiles"), properties.roleProfile())
					&& contains(profile.path("voice_profiles"), properties.voiceProfile())
					&& contains(profile.path("client_transports"), properties.clientTransport())) {
				return;
			}
		}
		throw retryableFailure(
				"QINIU_PROFILE_UNAVAILABLE",
				"Configured Qiniu realtime model, role, voice, or transport is unavailable");
	}

	private CreatedSession createSession(RealtimeConnectCommand command, String requestId) {
		var payload = objectMapper.createObjectNode()
				.put("app_id", properties.appId())
				.put("user_id", command.userId())
				.put("client_id", command.clientId())
				.put("model_profile", properties.modelProfile())
				.put("role_profile", properties.roleProfile())
				.put("voice_profile", properties.voiceProfile())
				.put("client_transport", properties.clientTransport())
				.put("region", properties.region());
		if (command.sceneType() != null) {
			payload.put("scenario", command.sceneType().name().toLowerCase());
		}
		HttpResponse<String> response = send(
				controlRequest(properties.controlUri(SESSIONS_PATH), requestId)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(writeJson(payload)))
					.build(),
				"QINIU_SESSION_CREATE_IO_ERROR");
		requireSuccess(response, "QINIU_SESSION_CREATE_FAILED", true);
		JsonNode root = parseJson(response.body(), "QINIU_SESSION_CREATE_INVALID");
		String sessionId = requiredText(root, "session_id", "QINIU_SESSION_ID_MISSING");
		requireSafeSessionId(sessionId);
		JsonNode endpoint = root.path("client_endpoint");
		String endpointUrl = requiredText(endpoint, "url", "QINIU_CLIENT_ENDPOINT_MISSING");
		String accessToken = requiredText(endpoint, "access_token", "QINIU_CLIENT_TOKEN_MISSING");
		long expiresAtMs = endpoint.path("expires_at_ms").longValue(0);
		Instant expiresAt = expiresAtMs > 0 ? Instant.ofEpochMilli(expiresAtMs) : null;
		RealtimeFlowLog.info(
				"realtime.provider.session.created provider={} providerSessionId={} traceId={} model={} voice={} transport={}",
				type(), sessionId, text(root, "trace_id"), properties.modelProfile(),
				properties.voiceProfile(), properties.clientTransport());
		return new CreatedSession(
				sessionId,
				text(root, "trace_id"),
				endpointUrl,
				accessToken,
				expiresAt);
	}

	private String exchangeSessionSdp(
			CreatedSession session,
			String offerSdp,
			String requestId) {
		URI endpoint;
		try {
			endpoint = properties.resolveClientEndpoint(session.endpointUrl());
		}
		catch (IllegalArgumentException exception) {
			throw nonRetryableFailure("QINIU_CLIENT_ENDPOINT_INVALID", exception.getMessage());
		}
		var payload = objectMapper.createObjectNode()
				.put("type", "offer")
				.put("sdp", offerSdp);
		HttpRequest request = HttpRequest.newBuilder()
				.uri(endpoint)
				.timeout(properties.readTimeout())
				.header("Authorization", "Bearer " + session.accessToken())
				.header("Content-Type", "application/json")
				.header("X-Request-ID", requestId)
				.POST(HttpRequest.BodyPublishers.ofString(writeJson(payload)))
				.build();
		RealtimeFlowLog.info(
				"flow.3.sdp.request provider={} providerSessionId={} model={} offerSdp={}",
				type(), session.sessionId(), properties.modelProfile(),
				RealtimeFlowLog.sdpSummary(offerSdp));
		HttpResponse<String> response = send(request, "QINIU_SIGNALING_IO_ERROR");
		requireSuccess(response, "QINIU_SIGNALING_FAILED", true);
		JsonNode answer = parseJson(response.body(), "QINIU_SIGNALING_INVALID");
		String answerSdp = requiredText(answer, "sdp", "QINIU_ANSWER_SDP_MISSING");
		RealtimeFlowLog.info(
				"flow.3.sdp.response provider={} providerSessionId={} status={} answerSdp={}",
				type(), session.sessionId(), response.statusCode(),
				RealtimeFlowLog.sdpSummary(answerSdp));
		return answerSdp;
	}

	private HttpRequest.Builder controlRequest(URI uri, String requestId) {
		return HttpRequest.newBuilder()
				.uri(uri)
				.timeout(properties.readTimeout())
				.header("Authorization", "Bearer " + properties.apiKey())
				.header("X-Request-ID", requestId);
	}

	private HttpResponse<String> send(HttpRequest request, String ioErrorCode) {
		try {
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.body() != null
					&& response.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
					> properties.maxResponseBytes()) {
				throw retryableFailure("QINIU_RESPONSE_TOO_LARGE", "Qiniu response exceeds the configured limit");
			}
			return response;
		}
		catch (IOException exception) {
			throw retryableFailure(ioErrorCode, "Failed to call Qiniu realtime API");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw nonRetryableFailure("QINIU_REQUEST_INTERRUPTED", "Qiniu realtime request was interrupted");
		}
	}

	private void requireSuccess(
			HttpResponse<String> response,
			String fallbackCode,
			boolean retryServerFailures) {
		int status = response.statusCode();
		if (status >= 200 && status < 300) return;
		JsonNode payload = parseJsonOrEmpty(response.body());
		String providerCode = text(payload, "code");
		String code = providerCode.isBlank() ? fallbackCode : "QINIU_" + providerCode.toUpperCase();
		String message = text(payload, "message");
		String safeMessage = message.isBlank()
				? "Qiniu realtime API returned HTTP " + status
				: message;
		if (status == 401 || status == 402 || status == 403 || status == 409
				|| status == 429 || (retryServerFailures && status >= 500)) {
			throw retryableFailure(code, safeMessage);
		}
		throw nonRetryableFailure(code, safeMessage);
	}

	private JsonNode parseJson(String body, String errorCode) {
		try {
			return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
		}
		catch (JacksonException exception) {
			throw retryableFailure(errorCode, "Qiniu realtime API returned invalid JSON");
		}
	}

	private JsonNode parseJsonOrEmpty(String body) {
		try {
			return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
		}
		catch (JacksonException exception) {
			return objectMapper.createObjectNode();
		}
	}

	private String writeJson(JsonNode payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		}
		catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize Qiniu realtime request", exception);
		}
	}

	private String requiredText(JsonNode node, String field, String errorCode) {
		String value = text(node, field);
		if (value.isBlank()) {
			throw retryableFailure(errorCode, "Qiniu realtime response is missing " + field);
		}
		return value;
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isTextual() ? value.asString().trim() : "";
	}

	private boolean contains(JsonNode array, String expected) {
		for (JsonNode value : array) {
			if (value.isTextual() && expected.equals(value.asString())) return true;
		}
		return false;
	}

	private String normalizedReason(String reason) {
		String normalized = reason == null ? "client_completed" : reason.trim();
		return normalized.isBlank() ? "client_completed" : normalized;
	}

	private String requireSafeSessionId(String sessionId) {
		String normalized = sessionId == null ? "" : sessionId.trim();
		if (!normalized.matches("[A-Za-z0-9._:-]{1,128}")) {
			throw nonRetryableFailure(
					"QINIU_SESSION_ID_INVALID",
					"Qiniu realtime session ID contains unsupported characters");
		}
		return normalized;
	}

	private void stopQuietly(String sessionId, String reason) {
		try {
			stopSession(sessionId, null, reason);
		}
		catch (RuntimeException exception) {
			RealtimeFlowLog.warn(
					"realtime.provider.stop.failed provider={} providerSessionId={} error={}",
					type(), sessionId, exception.getMessage());
		}
	}

	private record CreatedSession(
			String sessionId,
			String traceId,
			String endpointUrl,
			String accessToken,
			Instant expiresAt) {
	}
}
