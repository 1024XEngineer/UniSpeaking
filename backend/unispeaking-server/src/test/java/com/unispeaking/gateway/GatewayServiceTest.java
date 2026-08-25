package com.unispeaking.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

	@Test
	void rejectsEveryInvalidDependencyAndRequestShape() {
		GatewayKeyPool pool = mock(GatewayKeyPool.class);
		GatewayCredentialIssuer issuer = mock(GatewayCredentialIssuer.class);
		Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		Duration ttl = Duration.ofSeconds(1);
		assertThrows(IllegalArgumentException.class, () -> new GatewayService(null, issuer, clock, ttl));
		assertThrows(IllegalArgumentException.class, () -> new GatewayService(pool, null, clock, ttl));
		assertThrows(IllegalArgumentException.class, () -> new GatewayService(pool, issuer, null, ttl));
		assertThrows(IllegalArgumentException.class, () -> new GatewayService(pool, issuer, clock, null));
		assertThrows(IllegalArgumentException.class, () -> new GatewayService(pool, issuer, clock, Duration.ZERO));
		assertThrows(IllegalArgumentException.class, () -> new GatewayService(pool, issuer, clock, Duration.ofSeconds(-1)));

		GatewayService service = new GatewayService(pool, issuer, clock, ttl);
		assertThrows(IllegalArgumentException.class,
				() -> service.issueTemporaryCredential(null, ProviderType.QWEN, "model"));
		assertThrows(IllegalArgumentException.class,
				() -> service.issueTemporaryCredential(" ", ProviderType.QWEN, "model"));
		assertThrows(IllegalArgumentException.class,
				() -> service.issueTemporaryCredential("user", ProviderType.QWEN, null));
		assertThrows(IllegalArgumentException.class,
				() -> service.issueTemporaryCredential("user", ProviderType.QWEN, " "));
	}

	@Test
	void marksAcquiredKeyAsFailedWhenIssuerThrows() {
		GatewayKeyPool pool = mock(GatewayKeyPool.class);
		GatewayCredentialIssuer issuer = mock(GatewayCredentialIssuer.class);
		GatewayKey key = new GatewayKey("key-1", ProviderType.QWEN, "secret");
		Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		when(pool.acquire(ProviderType.QWEN)).thenReturn(key);
		when(issuer.issue(key, "model", Duration.ofSeconds(1), clock.instant()))
				.thenThrow(new IllegalStateException("down"));
		GatewayService service = new GatewayService(pool, issuer, clock, Duration.ofSeconds(1));

		assertThrows(IllegalStateException.class,
				() -> service.issueTemporaryCredential("user", ProviderType.QWEN, "model"));
		verify(pool).markFailure("key-1");
	}
}
