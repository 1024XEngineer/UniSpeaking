package com.unispeaking.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unispeaking.domain.vo.provider.ProviderType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatewayKeyPoolTest {

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    @Test
    void rotatesHealthyKeysAndSkipsAKeyDuringCooldown() {
        var pool = new GatewayKeyPool(
                List.of(
                        new GatewayKey("qwen-a", ProviderType.QWEN, "secret-a"),
                        new GatewayKey("qwen-b", ProviderType.QWEN, "secret-b")),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30));

        assertThat(pool.acquire(ProviderType.QWEN).keyId()).isEqualTo("qwen-a");
        pool.markFailure("qwen-a");
        assertThat(pool.acquire(ProviderType.QWEN).keyId()).isEqualTo("qwen-b");
        assertThat(pool.acquire(ProviderType.QWEN).keyId()).isEqualTo("qwen-b");
    }

    @Test
    void refusesWhenProviderHasNoHealthyKey() {
        var pool = new GatewayKeyPool(
                List.of(new GatewayKey("qwen-a", ProviderType.QWEN, "secret-a")),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30));
        pool.markFailure("qwen-a");

        assertThatThrownBy(() -> pool.acquire(ProviderType.QWEN))
                .isInstanceOf(GatewayException.class)
                .hasMessage("NO_HEALTHY_PROVIDER_KEY");
    }
}
