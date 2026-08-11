package com.unispeaking.gateway;

import java.time.Duration;
import java.time.Instant;

/** Provider adapter boundary for issuing a temporary credential. */
@FunctionalInterface
public interface GatewayCredentialIssuer {

    GatewayCredential issue(GatewayKey key, String model, Duration ttl, Instant issuedAt);
}
