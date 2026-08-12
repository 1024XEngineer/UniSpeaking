package com.unispeaking.admin.auth.adapters.memory;

import com.unispeaking.admin.auth.domain.AdminSession;
import com.unispeaking.admin.auth.ports.AdminSessionRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAdminSessionRepository implements AdminSessionRepository {
    private final Map<String, AdminSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(AdminSession session) {
        sessions.put(session.tokenHash(), session);
    }

    @Override
    public Optional<AdminSession> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(sessions.get(tokenHash));
    }

    @Override
    public void touch(String tokenHash, Instant lastSeenAt) {
        sessions.computeIfPresent(tokenHash, (ignored, session) -> new AdminSession(
                session.tokenHash(),
                session.adminId(),
                session.createdAt(),
                lastSeenAt,
                session.expiresAt(),
                session.revoked()));
    }

    @Override
    public void revoke(String tokenHash) {
        sessions.computeIfPresent(tokenHash, (ignored, session) -> new AdminSession(
                session.tokenHash(),
                session.adminId(),
                session.createdAt(),
                session.lastSeenAt(),
                session.expiresAt(),
                true));
    }

    @Override
    public void revokeAll(UUID adminId) {
        sessions.replaceAll((ignored, session) -> session.adminId().equals(adminId)
                ? new AdminSession(
                        session.tokenHash(),
                        session.adminId(),
                        session.createdAt(),
                        session.lastSeenAt(),
                        session.expiresAt(),
                        true)
                : session);
    }

    public int size() {
        return sessions.size();
    }
}
