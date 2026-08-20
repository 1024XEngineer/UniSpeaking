package com.unispeaking.infrastructure.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "realtime.qwen")
public record RealtimeProperties(
		String apiKey,
		String workspaceId,
		String model,
		String region,
		String temporaryKeyEndpoint,
		int temporaryKeyTtlSeconds,
		Duration connectTimeout,
		Duration readTimeout,
		int maxAnswerBytes) {

	public RealtimeProperties {
		apiKey = trim(apiKey);
		workspaceId = trim(workspaceId);
		model = trim(model);
		region = trim(region);
		temporaryKeyEndpoint = trimTrailingSlash(trim(temporaryKeyEndpoint));
	}

	@PostConstruct
	public void validate() {
		if (region.isBlank()) {
			throw new IllegalStateException("realtime.qwen.region must not be blank");
		}
		if (temporaryKeyEndpoint.isBlank()) {
			throw new IllegalStateException("realtime.qwen.temporary-key-endpoint must not be blank");
		}
		URI temporaryKeyUri = URI.create(temporaryKeyEndpoint);
		if (!temporaryKeyUri.isAbsolute() || (!"https".equalsIgnoreCase(temporaryKeyUri.getScheme())
				&& !"http".equalsIgnoreCase(temporaryKeyUri.getScheme()))) {
			throw new IllegalStateException("realtime.qwen.temporary-key-endpoint must be an absolute HTTP(S) URL");
		}
		if (temporaryKeyTtlSeconds < 1 || temporaryKeyTtlSeconds > 1800) {
			throw new IllegalStateException("realtime.qwen.temporary-key-ttl-seconds must be between 1 and 1800");
		}
		requirePositive(connectTimeout, "realtime.qwen.connect-timeout");
		requirePositive(readTimeout, "realtime.qwen.read-timeout");
		if (maxAnswerBytes <= 0) {
			throw new IllegalStateException("realtime.qwen.max-answer-bytes must be greater than zero");
		}
		if (!workspaceId.isBlank() && !model.isBlank()) {
			URI uri = URI.create(getWebRtcSdpExchangeUrl());
			if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())) {
				throw new IllegalStateException("Qwen WebRTC SDP exchange URL must be an absolute HTTPS URL");
			}
		}
	}

	private void requirePositive(Duration duration, String propertyName) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			throw new IllegalStateException(propertyName + " must be greater than zero");
		}
	}

	public String getWebRtcSdpExchangeUrl() {
		return getWebRtcSdpExchangeUrl(getModel());
	}

	public String getWebRtcSdpExchangeUrl(String realtimeModel) {
		return getWebRtcSdpExchangeUrl(realtimeModel, getWorkspaceId());
	}

	public String getWebRtcSdpExchangeUrl(String realtimeModel, String workspaceId) {
		String resolvedWorkspaceId = trim(workspaceId);
		String resolvedModel = trim(realtimeModel);
		if (resolvedWorkspaceId.isBlank() || resolvedModel.isBlank()
				|| !resolvedWorkspaceId.matches("[A-Za-z0-9-]{1,128}")) {
			return "";
		}
		return "https://" + resolvedWorkspaceId + "." + getRegion()
				+ ".maas.aliyuncs.com/api/v1/webrtc/realtime?model="
				+ URLEncoder.encode(resolvedModel, StandardCharsets.UTF_8);
	}
	public String getApiKey() {
		return apiKey;
	}
	public String getWorkspaceId() {
		return workspaceId;
	}
	public String getModel() {
		return model;
	}
	public String getRegion() {
		return region;
	}
	public String getTemporaryKeyEndpoint() { return temporaryKeyEndpoint; }
	public int getTemporaryKeyTtlSeconds() { return temporaryKeyTtlSeconds; }
	public Duration getConnectTimeout() { return connectTimeout; }
	public Duration getReadTimeout() { return readTimeout; }
	public int getMaxAnswerBytes() { return maxAnswerBytes; }

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static String trimTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
