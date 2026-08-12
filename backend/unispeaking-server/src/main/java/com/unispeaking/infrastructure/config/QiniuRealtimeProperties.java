package com.unispeaking.infrastructure.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "realtime.qiniu")
public record QiniuRealtimeProperties(
		String baseUrl,
		String apiKey,
		String appId,
		String modelProfile,
		String roleProfile,
		String voiceProfile,
		String clientTransport,
		String region,
		Duration readTimeout,
		int maxResponseBytes) {

	public QiniuRealtimeProperties {
		baseUrl = trimTrailingSlash(trim(baseUrl));
		apiKey = trim(apiKey);
		appId = trim(appId);
		modelProfile = trim(modelProfile);
		roleProfile = trim(roleProfile);
		voiceProfile = trim(voiceProfile);
		clientTransport = trim(clientTransport);
		region = trim(region);
	}

	@PostConstruct
	public void validate() {
		requireText(baseUrl, "realtime.qiniu.base-url");
		URI baseUri = URI.create(baseUrl);
		if (!baseUri.isAbsolute() || !"https".equalsIgnoreCase(baseUri.getScheme())) {
			throw new IllegalStateException("realtime.qiniu.base-url must be an absolute HTTPS URL");
		}
		requireText(appId, "realtime.qiniu.app-id");
		requireText(modelProfile, "realtime.qiniu.model-profile");
		requireText(roleProfile, "realtime.qiniu.role-profile");
		requireText(voiceProfile, "realtime.qiniu.voice-profile");
		if (!"platform_rtc".equals(clientTransport)) {
			throw new IllegalStateException("realtime.qiniu.client-transport must be platform_rtc");
		}
		requireText(region, "realtime.qiniu.region");
		if (readTimeout == null || readTimeout.isZero() || readTimeout.isNegative()) {
			throw new IllegalStateException("realtime.qiniu.read-timeout must be greater than zero");
		}
		if (maxResponseBytes <= 0) {
			throw new IllegalStateException("realtime.qiniu.max-response-bytes must be greater than zero");
		}
	}

	public URI controlUri(String path) {
		return URI.create(baseUrl + path);
	}

	public URI resolveClientEndpoint(String endpoint) {
		String normalized = trim(endpoint);
		if (normalized.isBlank()) return null;
		URI uri = URI.create(normalized);
		URI resolved = uri.isAbsolute() ? uri : URI.create(baseUrl).resolve(uri);
		if (!resolved.isAbsolute()
				|| !"https".equalsIgnoreCase(resolved.getScheme())) {
			throw new IllegalArgumentException("Qiniu realtime client endpoint must use HTTPS");
		}
		return resolved;
	}

	private static void requireText(String value, String propertyName) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(propertyName + " must not be blank");
		}
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static String trimTrailingSlash(String value) {
		while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
		return value;
	}
}
