package com.unispeaking.domain.vo.session;

import java.time.Instant;

/**
 * Bearer credential used while establishing a provider connection.
 * expiresAt is null for a long-lived server-side API key.
 */
public record RealtimeCredential(String bearerToken, Instant expiresAt) {

	public boolean temporary() {
		return expiresAt != null;
	}
}
