package com.unispeaking.infrastructure.realtime;

import com.unispeaking.infrastructure.config.TurnProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TurnCredentialIssuer {

	private final TurnProperties properties;
	private final Clock clock;

	@Autowired
	public TurnCredentialIssuer(TurnProperties properties) {
		this(properties, Clock.systemUTC());
	}

	TurnCredentialIssuer(TurnProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	public IssuedTurnCredential issue() {
		Instant expiresAt = clock.instant().plus(properties.credentialTtl());
		String username = expiresAt.getEpochSecond() + ":" + UUID.randomUUID();
		return new IssuedTurnCredential(username, sign(username), expiresAt);
	}

	private String sign(String username) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(
					properties.sharedSecret().getBytes(StandardCharsets.UTF_8),
					"HmacSHA1"));
			return Base64.getEncoder().encodeToString(
					mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to issue TURN credential", exception);
		}
	}

	public record IssuedTurnCredential(String username, String credential, Instant expiresAt) {}
}
