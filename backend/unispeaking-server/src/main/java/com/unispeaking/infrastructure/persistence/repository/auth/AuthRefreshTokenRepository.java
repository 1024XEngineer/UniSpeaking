package com.unispeaking.infrastructure.persistence.repository.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRefreshTokenRepository {
    private final JdbcTemplate jdbc;

    public AuthRefreshTokenRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(String digest, UUID userId, Instant now, Instant expiresAt) {
        jdbc.update("insert into auth_refresh_tokens (token_digest,user_id,created_at,last_used_at,expires_at) values (?,?,?,?,?)",
                digest, userId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(expiresAt));
    }

    public int consume(String digest, Instant now, Instant idleCutoff) {
        return jdbc.update("update auth_refresh_tokens set revoked_at=?, last_used_at=? "
                        + "where token_digest=? and revoked_at is null and expires_at>? and last_used_at>?",
                Timestamp.from(now), Timestamp.from(now), digest, Timestamp.from(now), Timestamp.from(idleCutoff));
    }

    public Record find(String digest) {
        return jdbc.query("select token_digest,user_id,created_at,last_used_at,expires_at,revoked_at "
                        + "from auth_refresh_tokens where token_digest=?", (rs, row) -> new Record(
                        rs.getString("token_digest"), rs.getObject("user_id", UUID.class),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("last_used_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()), digest)
                .stream().findFirst().orElse(null);
    }

    public void revoke(String digest, Instant now) {
        jdbc.update("update auth_refresh_tokens set revoked_at=? where token_digest=? and revoked_at is null",
                Timestamp.from(now), digest);
    }

    public void revokeAll(UUID userId, Instant now) {
        jdbc.update("update auth_refresh_tokens set revoked_at=? where user_id=? and revoked_at is null",
                Timestamp.from(now), userId);
    }

    public int deleteExpired(Instant before) {
        return jdbc.update("delete from auth_refresh_tokens where token_digest in ("
                        + "select token_digest from auth_refresh_tokens where expires_at < ? limit 500)",
                Timestamp.from(before));
    }

    public record Record(String digest, UUID userId, Instant createdAt, Instant lastUsedAt,
                         Instant expiresAt, Instant revokedAt) {}
}
