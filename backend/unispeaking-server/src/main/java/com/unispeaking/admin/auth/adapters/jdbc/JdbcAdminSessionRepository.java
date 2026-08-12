package com.unispeaking.admin.auth.adapters.jdbc;

import com.unispeaking.admin.auth.domain.AdminSession;
import com.unispeaking.admin.auth.ports.AdminSessionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcAdminSessionRepository implements AdminSessionRepository {
    private final JdbcTemplate jdbc;

    public JdbcAdminSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(AdminSession session) {
        jdbc.update(
                "insert into admin_sessions (token_hash, admin_id, created_at, last_seen_at, expires_at, revoked) values (?, ?, ?, ?, ?, ?)",
                session.tokenHash(), session.adminId(), Timestamp.from(session.createdAt()), Timestamp.from(session.lastSeenAt()),
                Timestamp.from(session.expiresAt()), session.revoked());
    }

    @Override
    public Optional<AdminSession> findByTokenHash(String tokenHash) {
        var rows = jdbc.query(
                "select token_hash, admin_id, created_at, last_seen_at, expires_at, revoked from admin_sessions where token_hash = ?",
                (rs, row) -> new AdminSession(
                        rs.getString("token_hash"), rs.getObject("admin_id", UUID.class),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("last_seen_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant(), rs.getBoolean("revoked")),
                tokenHash);
        return rows.stream().findFirst();
    }

    @Override
    public void touch(String tokenHash, Instant lastSeenAt) {
        jdbc.update("update admin_sessions set last_seen_at = ? where token_hash = ? and revoked = false",
                Timestamp.from(lastSeenAt), tokenHash);
    }

    @Override
    public void revoke(String tokenHash) {
        jdbc.update("update admin_sessions set revoked = true where token_hash = ?", tokenHash);
    }

    @Override
    public void revokeAll(UUID adminId) {
        jdbc.update("update admin_sessions set revoked = true where admin_id = ?", adminId);
    }
}
