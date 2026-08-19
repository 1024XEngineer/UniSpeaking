package com.unispeaking.infrastructure.persistence.repository.auth;

import com.unispeaking.service.auth.EmailAuthStore;
import java.sql.Timestamp;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;

public final class JdbcEmailAuthStore implements EmailAuthStore {
    private final JdbcTemplate jdbc;

    public JdbcEmailAuthStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveChallenge(UUID id, String email, byte[] codeDigest, Instant expiresAt, Instant createdAt) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "insert into auth_email_challenges (id, email, code_digest, expires_at, created_at) values (?, ?, ?, ?, ?)");
            statement.setObject(1, id);
            statement.setString(2, email);
            statement.setBytes(3, codeDigest);
            statement.setTimestamp(4, Timestamp.from(expiresAt));
            statement.setTimestamp(5, Timestamp.from(createdAt));
            return statement;
        });
    }

    @Override
    public Optional<ChallengeRecord> findChallenge(UUID id) {
        var rows = jdbc.query(
                "select id, email, code_digest, expires_at, consumed_at from auth_email_challenges where id = ?",
                (rs, row) -> new ChallengeRecord(
                        rs.getObject("id", UUID.class), rs.getString("email"), rs.getBytes("code_digest"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant()),
                id);
        return rows.stream().findFirst();
    }

    @Override
    public boolean consumeChallenge(UUID id, Instant consumedAt) {
        return jdbc.update(
                "update auth_email_challenges set consumed_at = ? where id = ? and consumed_at is null and expires_at > ?",
                Timestamp.from(consumedAt), id, Timestamp.from(consumedAt)) == 1;
    }

    @Override
    public boolean saveUser(UUID id, String email, String passwordHash, String nickname,
            Instant createdAt, Instant emailVerifiedAt) {
        try {
            return jdbc.execute((ConnectionCallback<Boolean>) connection -> {
                var previousAutoCommit = connection.getAutoCommit();
                try {
                    connection.setAutoCommit(false);
                    try (var userStatement = connection.prepareStatement(
                            "insert into users (id, username, password_hash, nickname, role, status, auth_version, "
                                    + "email_verified_at, created_at, updated_at) "
                                    + "values (?, ?, ?, ?, 'USER', 'ACTIVE', 0, ?, ?, ?)")) {
                        userStatement.setObject(1, id);
                        userStatement.setString(2, email);
                        userStatement.setString(3, passwordHash);
                        userStatement.setString(4, nickname);
                        userStatement.setTimestamp(5, Timestamp.from(emailVerifiedAt));
                        userStatement.setTimestamp(6, Timestamp.from(createdAt));
                        userStatement.setTimestamp(7, Timestamp.from(createdAt));
                        userStatement.executeUpdate();
                    }
                    try (var entitlementStatement = connection.prepareStatement(
                            "insert into user_entitlements (user_id, plan_code, plan_name, quota_date, quota_seconds, used_seconds, status, updated_at) "
                                    + "values (?, 'free', 'Free', current_date, 600, 0, 'active', current_timestamp)")) {
                        entitlementStatement.setObject(1, id);
                        entitlementStatement.executeUpdate();
                    }
                    connection.commit();
                    return true;
                } catch (Exception exception) {
                    connection.rollback();
                    if (exception instanceof org.springframework.dao.DataAccessException dataAccessException) {
                        throw dataAccessException;
                    }
                    if (exception instanceof SQLException sqlException
                            && sqlException.getSQLState() != null
                            && sqlException.getSQLState().startsWith("23")) {
                        throw new org.springframework.dao.DuplicateKeyException(
                                "Email identity already exists", sqlException);
                    }
                    throw new org.springframework.dao.DataAccessResourceFailureException(
                            "Unable to persist user entitlement", exception);
                } finally {
                    connection.setAutoCommit(previousAutoCommit);
                }
            });
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    @Override
    public void ensureGovernance(UserRecord user, Instant now) {
        int updated = jdbc.update(
                "update users set email_verified_at = coalesce(email_verified_at, ?) "
                        + "where id = ? and lower(username) = lower(?)",
                Timestamp.from(now), user.id(), user.email());
        if (updated == 0) {
            throw new DataIntegrityViolationException("User identity no longer exists");
        }
        if (jdbc.update("update user_entitlements set updated_at = updated_at where user_id = ?", user.id()) == 0) {
            try {
                jdbc.update("insert into user_entitlements (user_id, plan_code, plan_name, quota_date, quota_seconds, used_seconds, status, updated_at) "
                                + "values (?, 'free', 'Free', current_date, 600, 0, 'active', current_timestamp)",
                        user.id());
            } catch (DataIntegrityViolationException ignored) {
                // A concurrent login already created the default entitlement.
            }
        }
    }

    @Override
    public Optional<UserRecord> findUserByEmail(String email) {
        var rows = jdbc.query(
                "select id, username as email, password_hash from users where lower(username) = lower(?)",
                (rs, row) -> new UserRecord(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash")),
                email);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<UserRecord> findUserById(UUID id) {
        var rows = jdbc.query(
                "select id, username as email, password_hash from users where id = ?",
                (rs, row) -> new UserRecord(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash")),
                id);
        return rows.stream().findFirst();
    }

    @Override
    public void updatePassword(String email, String passwordHash, Instant updatedAt) {
        int updated = jdbc.update(
                "update users set password_hash = ?, auth_version = auth_version + 1, updated_at = ? "
                        + "where lower(username) = lower(?)",
                passwordHash, Timestamp.from(updatedAt), email);
        if (updated == 0) {
            throw new DataIntegrityViolationException("Email identity no longer exists");
        }
    }

    @Override
    public void revokeSessionsByEmail(String email, Instant revokedAt) {
        jdbc.update(
                "update user_sessions set revoked_at = ? where revoked_at is null and user_id in ("
                        + "select id from users where lower(username) = lower(?))",
                Timestamp.from(revokedAt), email);
    }

    @Override
    public void saveSession(String tokenDigest, UUID userId, Instant createdAt, Instant lastSeenAt, Instant expiresAt) {
        jdbc.update(
                "insert into user_sessions (token_digest, user_id, created_at, last_seen_at, expires_at) values (?, ?, ?, ?, ?)",
                tokenDigest, userId, Timestamp.from(createdAt), Timestamp.from(lastSeenAt), Timestamp.from(expiresAt));
    }

    @Override
    public Optional<SessionRecord> findSession(String tokenDigest) {
        var rows = jdbc.query(
                "select token_digest, user_id, created_at, last_seen_at, expires_at, revoked_at from user_sessions where token_digest = ?",
                (rs, row) -> new SessionRecord(
                        rs.getString("token_digest"), rs.getObject("user_id", UUID.class),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("last_seen_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()),
                tokenDigest);
        return rows.stream().findFirst();
    }

    @Override
    public void revokeSession(String tokenDigest) {
        jdbc.update("update user_sessions set revoked_at = current_timestamp where token_digest = ? and revoked_at is null", tokenDigest);
    }
}
