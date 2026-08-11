package com.unispeaking.infrastructure.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "realtime.qiniu")
public record QiniuRealtimeProperties(
		String baseUrl,
		String apiKey,
		String appId,
		String modelProfile,
		String roleProfile,
		String scenario,
		Map<String, String> voiceProfiles,
		Duration connectTimeout,
		Duration readTimeout,
		int maxResponseBytes) {

	public QiniuRealtimeProperties {
		baseUrl = trimTrailingSlash(trim(baseUrl));
		apiKey = trim(apiKey);
		appId = trim(appId);
		modelProfile = trim(modelProfile);
		roleProfile = trim(roleProfile);
		scenario = trim(scenario);
		Map<String, String> normalizedVoices = new LinkedHashMap<>();
		if (voiceProfiles != null) {
			voiceProfiles.forEach((alias, profile) -> {
				String normalizedAlias = trim(alias).toLowerCase(Locale.ROOT);
				String normalizedProfile = trim(profile);
				if (!normalizedAlias.isBlank() && !normalizedProfile.isBlank()) {
					normalizedVoices.put(normalizedAlias, normalizedProfile);
				}
			});
		}
		voiceProfiles = Map.copyOf(normalizedVoices);
	}

	@PostConstruct
	public void validate() {
		if (baseUrl.isBlank()) {
			throw new IllegalStateException("realtime.qiniu.base-url must not be blank");
		}
		URI baseUri = URI.create(baseUrl);
		if (!baseUri.isAbsolute()
				|| baseUri.getHost() == null
				|| (!"https".equalsIgnoreCase(baseUri.getScheme())
						&& !"http".equalsIgnoreCase(baseUri.getScheme()))) {
			throw new IllegalStateException(
					"realtime.qiniu.base-url must be an absolute HTTP(S) URL");
		}
		requirePositive(connectTimeout, "realtime.qiniu.connect-timeout");
		requirePositive(readTimeout, "realtime.qiniu.read-timeout");
		if (maxResponseBytes <= 0) {
			throw new IllegalStateException(
					"realtime.qiniu.max-response-bytes must be greater than zero");
		}
	}

	public URI baseUri() {
		return URI.create(baseUrl);
	}

	public String voiceProfile(String logicalVoiceId) {
		return voiceProfiles.getOrDefault(
				trim(logicalVoiceId).toLowerCase(Locale.ROOT),
				"");
	}

	private void requirePositive(Duration duration, String propertyName) {
		if (duration == null || duration.isZero() || duration.isNegative()) {
			throw new IllegalStateException(propertyName + " must be greater than zero");
		}
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static String trimTrailingSlash(String value) {
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}
		return value;
	}
}
