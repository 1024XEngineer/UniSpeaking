package com.unispeaking.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.unispeaking.domain.vo.provider.ProviderType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatewayServiceTest {

    @Test
    void issuesAProviderCredentialWithTheConfiguredGatewayTtl() {
        var now = Instant.parse("2026-08-07T00:00:00Z");
        var pool = new GatewayKeyPool(
                List.of(new GatewayKey("qwen-a", ProviderType.QWEN, "parent-secret")),
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofSeconds(30));
        var issuer = (GatewayCredentialIssuer) (key, model, ttl, issuedAt) ->
                new GatewayCredential(key.provider(), model, "temporary-token", issuedAt, issuedAt.plus(ttl), key.keyId());
        var service = new GatewayService(pool, issuer, Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(300));

        var credential = service.issueTemporaryCredential(
                "user-1", ProviderType.QWEN, "qwen3.5-omni-flash-realtime");

        assertThat(credential.keyId()).isEqualTo("qwen-a");
        assertThat(credential.expiresAt()).isEqualTo(now.plusSeconds(300));
        assertThat(credential.bearerToken()).isEqualTo("temporary-token");
    }
}
