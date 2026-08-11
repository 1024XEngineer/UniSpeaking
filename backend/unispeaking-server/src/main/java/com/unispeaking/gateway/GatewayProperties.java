package com.unispeaking.gateway;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime knobs for the local gateway boundary. */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        boolean enabled,
        int credentialTtlSeconds,
        int sessionLeaseSeconds,
        int keyFailureCooldownSeconds) {

    public GatewayProperties {
        if (credentialTtlSeconds < 1 || credentialTtlSeconds > 1800) {
            throw new IllegalArgumentException("gateway credential TTL must be between 1 and 1800 seconds");
        }
        if (sessionLeaseSeconds < 1) {
            throw new IllegalArgumentException("gateway session lease must be positive");
        }
        if (keyFailureCooldownSeconds < 1) {
            throw new IllegalArgumentException("gateway key failure cooldown must be positive");
        }
    }

    public Duration credentialTtl() {
        return Duration.ofSeconds(credentialTtlSeconds);
    }

    public Duration sessionLease() {
        return Duration.ofSeconds(sessionLeaseSeconds);
    }

    public Duration keyFailureCooldown() {
        return Duration.ofSeconds(keyFailureCooldownSeconds);
    }
}
