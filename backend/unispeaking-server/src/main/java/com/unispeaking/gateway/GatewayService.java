package com.unispeaking.gateway;

import com.unispeaking.domain.vo.provider.ProviderType;
import java.time.Clock;
import java.time.Duration;

/** Orchestrates key selection and provider temporary-credential issuance. */
public final class GatewayService {
    private final GatewayKeyPool keyPool;
    private final GatewayCredentialIssuer credentialIssuer;
    private final Clock clock;
    private final Duration credentialTtl;

    public GatewayService(
            GatewayKeyPool keyPool,
            GatewayCredentialIssuer credentialIssuer,
            Clock clock,
            Duration credentialTtl) {
        if (keyPool == null || credentialIssuer == null || clock == null
                || credentialTtl == null || credentialTtl.isNegative() || credentialTtl.isZero()) {
            throw new IllegalArgumentException("gateway service configuration is invalid");
        }
        this.keyPool = keyPool;
        this.credentialIssuer = credentialIssuer;
        this.clock = clock;
        this.credentialTtl = credentialTtl;
    }

    public GatewayCredential issueTemporaryCredential(
            String userId,
            ProviderType provider,
            String model) {
        if (userId == null || userId.isBlank() || model == null || model.isBlank()) {
            throw new IllegalArgumentException("userId and model are required");
        }
        GatewayKey key = keyPool.acquire(provider);
        try {
            GatewayCredential credential = credentialIssuer.issue(key, model, credentialTtl, clock.instant());
            keyPool.markSuccess(key.keyId());
            return credential;
        } catch (RuntimeException exception) {
            keyPool.markFailure(key.keyId());
            throw exception;
        }
    }
}
