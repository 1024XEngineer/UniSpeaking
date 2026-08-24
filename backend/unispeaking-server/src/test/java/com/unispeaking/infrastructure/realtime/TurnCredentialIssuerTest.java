package com.unispeaking.infrastructure.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.unispeaking.infrastructure.config.TurnProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TurnCredentialIssuerTest {

	@Test
	void issuesCoturnRestCredentialWithConfiguredExpiration() throws Exception {
		String secret = "01234567890123456789012345678901";
		TurnProperties properties = new TurnProperties(
				true,
				List.of("turn:turn.example.cn:443?transport=udp"),
				secret,
				Duration.ofMinutes(5),
				100,
				Set.of());
		Instant now = Instant.parse("2026-08-19T03:00:00Z");
		TurnCredentialIssuer issuer = new TurnCredentialIssuer(
				properties,
				Clock.fixed(now, ZoneOffset.UTC));

		TurnCredentialIssuer.IssuedTurnCredential credential = issuer.issue();

		assertEquals(now.plus(Duration.ofMinutes(5)), credential.expiresAt());
		assertFalse(credential.username().substring(credential.username().indexOf(':') + 1).isBlank());
		assertEquals(now.plus(Duration.ofMinutes(5)).getEpochSecond(),
				Long.parseLong(credential.username().substring(0, credential.username().indexOf(':'))));
		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
		assertEquals(
				Base64.getEncoder().encodeToString(mac.doFinal(
						credential.username().getBytes(StandardCharsets.UTF_8))),
				credential.credential());
	}
}
