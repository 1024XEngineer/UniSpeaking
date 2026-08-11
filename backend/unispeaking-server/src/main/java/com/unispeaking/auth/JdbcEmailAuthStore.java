package com.unispeaking.auth;

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
    public boolean saveUser(UUID id, String email, String passwordHash, Instant createdAt, Instant emailVerifiedAt) {
        try {
            return jdbc.execute((ConnectionCallback<Boolean>) connection -> {
                var previousAutoCommit = connection.getAutoCommit();
                try {
                    connection.setAutoCommit(false);
                    try (var userStatement = connection.prepareStatement(
                            "insert into \"user\" (id, username, password_hash, nickname, role, status, auth_version, created_at, updated_at) "
                                    + "values (?, ?, ?, null, 'USER', 'ACTIVE', 0, ?, ?)")) {
                        userStatement.setObject(1, id);
                        userStatement.setString(2, email);
                        userStatement.setString(3, passwordHash);
                        userStatement.setTimestamp(4, Timestamp.from(createdAt));
                        userStatement.setTimestamp(5, Timestamp.from(createdAt));
                        userStatement.executeUpdate();
                    }
                    try (var identityStatement = connection.prepareStatement(
                            "insert into app_users (id, email, password_hash, created_at, email_verified_at) "
                                    + "values (?, ?, ?, ?, ?)")) {
                        identityStatement.setObject(1, id);
                        identityStatement.setString(2, email);
                        identityStatement.setString(3, passwordHash);
                        identityStatement.setTimestamp(4, Timestamp.from(createdAt));
                        identityStatement.setTimestamp(5, Timestamp.from(emailVerifiedAt));
                        identityStatement.executeUpdate();
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
        int updated = jdbc.update("update app_users set email = ?, password_hash = ?, email_verified_at = ? where id = ?",
                user.email(), user.passwordHash(), Timestamp.from(now), user.id());
        if (updated == 0) {
            try {
                jdbc.update("insert into app_users (id, email, password_hash, created_at, email_verified_at) "
                                + "values (?, ?, ?, ?, ?)",
                        user.id(), user.email(), user.passwordHash(), Timestamp.from(now), Timestamp.from(now));
            } catch (DataIntegrityViolationException ignored) {
                // A concurrent login already created the projection; the identity is still valid.
            }
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
                "select id, email, password_hash from app_users where email = ?",
                (rs, row) -> new UserRecord(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash")),
                email);
        if (!rows.isEmpty()) return rows.stream().findFirst();
        rows = jdbc.query(
                "select id, username as email, password_hash from \"user\" where username = ?",
                (rs, row) -> new UserRecord(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash")),
                email);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<UserRecord> findUserById(UUID id) {
        var rows = jdbc.query(
                "select id, username as email, password_hash from \"user\" where id = ?",
                (rs, row) -> new UserRecord(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash")),
                id);
        if (!rows.isEmpty()) return rows.stream().findFirst();
        rows = jdbc.query(
                "select id, email, password_hash from app_users where id = ?",
                (rs, row) -> new UserRecord(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash")),
                id);
        return rows.stream().findFirst();
    }

    @Override
    public void updatePassword(String email, String passwordHash, Instant updatedAt) {
        int identityUpdates = jdbc.update(
                "update app_users set password_hash = ? where lower(email) = lower(?)",
                passwordHash, email);
        int businessUpdates = jdbc.update(
                "update \"user\" set password_hash = ?, auth_version = auth_version + 1, updated_at = ? "
                        + "where lower(username) = lower(?)",
                passwordHash, Timestamp.from(updatedAt), email);
        int updated = identityUpdates + businessUpdates;
        if (updated == 0) {
            throw new DataIntegrityViolationException("Email identity no longer exists");
        }
    }

    @Override
    public void revokeSessionsByEmail(String email, Instant revokedAt) {
        jdbc.update(
                "update user_sessions set revoked_at = ? where revoked_at is null and user_id in ("
                        + "select id from app_users where lower(email) = lower(?) "
                        + "union select id from \"user\" where lower(username) = lower(?))",
                Timestamp.from(revokedAt), email, email);
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
