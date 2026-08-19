package com.unispeaking.infrastructure.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "realtime.turn")
public record TurnProperties(
		boolean enabled,
		List<String> urls,
		String sharedSecret,
		Duration credentialTtl,
		int rolloutPercentage,
		Set<String> relayTestUserIds) {

	public TurnProperties {
		urls = urls == null ? List.of() : urls.stream()
				.map(TurnProperties::trim)
				.filter(value -> !value.isBlank())
				.toList();
		sharedSecret = trim(sharedSecret);
		relayTestUserIds = relayTestUserIds == null ? Set.of() : relayTestUserIds.stream()
				.map(TurnProperties::trim)
				.filter(value -> !value.isBlank())
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	@PostConstruct
	public void validate() {
		if (rolloutPercentage < 0 || rolloutPercentage > 100) {
			throw new IllegalStateException("realtime.turn.rollout-percentage must be between 0 and 100");
		}
		if (credentialTtl == null || credentialTtl.compareTo(Duration.ofMinutes(1)) < 0
				|| credentialTtl.compareTo(Duration.ofHours(1)) > 0) {
			throw new IllegalStateException("realtime.turn.credential-ttl must be between 1 minute and 1 hour");
		}
		if (!enabled) return;
		if (urls.isEmpty()) {
			throw new IllegalStateException("realtime.turn.urls must not be empty when TURN is enabled");
		}
		for (String url : urls) {
			URI uri = URI.create(url);
			if (!"turn".equalsIgnoreCase(uri.getScheme())
					|| !url.toLowerCase().contains("transport=udp")) {
				throw new IllegalStateException("realtime.turn.urls must contain TURN UDP URLs only");
			}
		}
		if (sharedSecret.length() < 32) {
			throw new IllegalStateException("realtime.turn.shared-secret must contain at least 32 characters");
		}
	}

	public boolean canForceRelay(String userId) {
		return relayTestUserIds.contains(trim(userId));
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}
}
