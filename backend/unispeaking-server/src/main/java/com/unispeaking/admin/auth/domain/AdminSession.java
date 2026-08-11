package com.unispeaking.admin.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record AdminSession(
        String tokenHash,
        UUID adminId,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        boolean revoked) {

    public boolean activeAt(Instant now, long idleSeconds) {
        return !revoked
                && now.isBefore(expiresAt)
                && now.isBefore(lastSeenAt.plusSeconds(idleSeconds));
    }
}
