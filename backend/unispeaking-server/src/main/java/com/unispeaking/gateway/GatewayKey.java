package com.unispeaking.gateway;

import com.unispeaking.domain.vo.provider.ProviderType;

/** A server-side parent key. The secret is never part of status output. */
public record GatewayKey(String keyId, ProviderType provider, String secret) {

    public GatewayKey {
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("secret must not be blank");
        }
    }

    @Override
    public String toString() {
        return "GatewayKey[keyId=" + keyId + ", provider=" + provider + ", secret=***]";
    }
}
