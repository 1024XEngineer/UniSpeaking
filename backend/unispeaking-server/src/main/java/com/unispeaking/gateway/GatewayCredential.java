package com.unispeaking.gateway;

import com.unispeaking.domain.vo.provider.ProviderType;
import java.time.Instant;

/** Temporary credential returned to the server-side session flow. */
public record GatewayCredential(
        ProviderType provider,
        String model,
        String bearerToken,
        Instant issuedAt,
        Instant expiresAt,
        String keyId) {

    public GatewayCredential {
        if (provider == null || model == null || model.isBlank()
                || bearerToken == null || bearerToken.isBlank()
                || issuedAt == null || expiresAt == null || keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("gateway credential fields are incomplete");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("gateway credential must expire after it is issued");
        }
    }

    @Override
    public String toString() {
        return "GatewayCredential[provider=" + provider + ", model=" + model
                + ", bearerToken=***, issuedAt=" + issuedAt + ", expiresAt=" + expiresAt
                + ", keyId=" + keyId + "]";
    }
}
