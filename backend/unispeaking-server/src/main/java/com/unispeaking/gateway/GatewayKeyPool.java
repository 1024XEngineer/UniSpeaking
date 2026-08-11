package com.unispeaking.gateway;

import com.unispeaking.domain.vo.provider.ProviderType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Thread-safe round-robin pool with failure cooldown. */
public final class GatewayKeyPool {
    private final List<GatewayKey> keys;
    private final Clock clock;
    private final Duration failureCooldown;
    private final Map<String, Instant> cooldownUntil = new HashMap<>();
    private int cursor;

    public GatewayKeyPool(List<GatewayKey> keys, Clock clock, Duration failureCooldown) {
        if (keys == null || keys.isEmpty()) {
            throw new IllegalArgumentException("at least one gateway key is required");
        }
        if (clock == null || failureCooldown == null || failureCooldown.isNegative()
                || failureCooldown.isZero()) {
            throw new IllegalArgumentException("gateway key pool timing is invalid");
        }
        this.keys = List.copyOf(new ArrayList<>(keys));
        this.clock = clock;
        this.failureCooldown = failureCooldown;
    }

    public synchronized GatewayKey acquire(ProviderType provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        Instant now = clock.instant();
        for (int offset = 0; offset < keys.size(); offset++) {
            int index = (cursor + offset) % keys.size();
            GatewayKey key = keys.get(index);
            if (key.provider() != provider) {
                continue;
            }
            Instant unavailableUntil = cooldownUntil.get(key.keyId());
            if (unavailableUntil != null && unavailableUntil.isAfter(now)) {
                continue;
            }
            cursor = (index + 1) % keys.size();
            return key;
        }
        throw new GatewayException("NO_HEALTHY_PROVIDER_KEY");
    }

    public synchronized void markSuccess(String keyId) {
        cooldownUntil.remove(keyId);
    }

    public synchronized void markFailure(String keyId) {
        if (keyId != null && !keyId.isBlank()) {
            cooldownUntil.put(keyId, clock.instant().plus(failureCooldown));
        }
    }

    public synchronized List<GatewayKeyStatus> statuses() {
        Instant now = clock.instant();
        return keys.stream()
                .map(key -> {
                    Instant until = cooldownUntil.get(key.keyId());
                    boolean healthy = until == null || !until.isAfter(now);
                    return new GatewayKeyStatus(key.keyId(), key.provider(), healthy,
                            healthy ? null : until);
                })
                .toList();
    }

    public record GatewayKeyStatus(
            String keyId,
            ProviderType provider,
            boolean healthy,
            Instant cooldownUntil) {
    }
}
