package com.unispeaking.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("conversation.redis")
public record FreeChatRedisProperties(
		String keyPrefix,
		Duration ttl) {

	public FreeChatRedisProperties {
		keyPrefix = keyPrefix == null || keyPrefix.isBlank()
				? "unispeaking:free-chat:session"
				: keyPrefix.trim().replaceAll(":+$", "");
		ttl = ttl == null ? Duration.ofHours(24) : ttl;
		if (ttl.isZero() || ttl.isNegative()) {
			throw new IllegalArgumentException("conversation Redis TTL must be positive");
		}
	}
}
