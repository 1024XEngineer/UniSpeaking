package com.unispeaking.admin.auth.ports;

import com.unispeaking.admin.auth.domain.AdminSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AdminSessionRepository {
    void save(AdminSession session);

    Optional<AdminSession> findByTokenHash(String tokenHash);

    void touch(String tokenHash, Instant lastSeenAt);

    void revoke(String tokenHash);

    void revokeAll(UUID adminId);
}
