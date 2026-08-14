package com.unispeaking.infrastructure.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.qiniu-maas")
public record QiniuMaasProperties(
		String baseUrl,
		String apiKey,
		String primaryModel,
		String fallbackModel,
		Duration connectTimeout,
		Duration readTimeout,
		int maxResponseBytes,
		int maxOutputTokens) {

	private static final String DEFAULT_BASE_URL = "https://api.qnaigc.com/v1";
	private static final String DEFAULT_PRIMARY_MODEL = "deepseek/deepseek-v4-flash";
	private static final String DEFAULT_FALLBACK_MODEL = "qwen/qwen3.5-plus";
	private static final Set<String> TRUSTED_HOSTS = Set.of(
			"api.qnaigc.com",
			"openai.sufy.com");

	public QiniuMaasProperties {
		baseUrl = trimTrailingSlash(defaultIfBlank(baseUrl, DEFAULT_BASE_URL));
		apiKey = trim(apiKey);
		primaryModel = defaultIfBlank(primaryModel, DEFAULT_PRIMARY_MODEL);
		fallbackModel = defaultIfBlank(fallbackModel, DEFAULT_FALLBACK_MODEL);
		connectTimeout = positiveOrDefault(connectTimeout, Duration.ofSeconds(10));
		readTimeout = positiveOrDefault(readTimeout, Duration.ofSeconds(90));
		maxResponseBytes = maxResponseBytes > 0 ? maxResponseBytes : 2 * 1024 * 1024;
		maxOutputTokens = maxOutputTokens > 0 ? maxOutputTokens : 4096;
	}

	@PostConstruct
	public void validate() {
		URI uri;
		try {
			uri = URI.create(baseUrl);
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalStateException("ai.qiniu-maas.base-url must be a valid URI", exception);
		}
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		if (!uri.isAbsolute()
				|| !"https".equalsIgnoreCase(uri.getScheme())
				|| !TRUSTED_HOSTS.contains(host)
				|| uri.getUserInfo() != null
				|| uri.getPort() != -1
				|| !"/v1".equals(uri.getPath())
				|| uri.getRawQuery() != null
				|| uri.getRawFragment() != null) {
			throw new IllegalStateException(
					"ai.qiniu-maas.base-url must be a trusted Qiniu MaaS v1 endpoint");
		}
		if (primaryModel.equalsIgnoreCase(fallbackModel)) {
			throw new IllegalStateException(
					"ai.qiniu-maas primary and fallback models must be different");
		}
	}

	public URI chatCompletionsUri() {
		return URI.create(baseUrl + "/chat/completions");
	}

	private static Duration positiveOrDefault(Duration value, Duration fallback) {
		return value == null || value.isZero() || value.isNegative() ? fallback : value;
	}

	private static String defaultIfBlank(String value, String fallback) {
		String normalized = trim(value);
		return normalized.isBlank() ? fallback : normalized;
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static String trimTrailingSlash(String value) {
		while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
		return value;
	}
}
