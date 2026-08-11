package com.unispeaking.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class JdbcEmailAuthStoreTest {
    private JdbcEmailAuthStore store;
    private EmbeddedDatabase database;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .addScript("db/test-schema.sql")
                .build();
        store = new JdbcEmailAuthStore(new JdbcTemplate(database));
    }

    @Test
    void persistsUserChallengeAndSessionAcrossStoreCalls() {
        var userId = UUID.randomUUID();
        var challengeId = UUID.randomUUID();
        var now = Instant.parse("2026-08-06T08:00:00Z");
        store.saveChallenge(challengeId, "person@example.com", new byte[] {1, 2}, now.plusSeconds(600), now);
        assertThat(store.findChallenge(challengeId).orElseThrow().email()).isEqualTo("person@example.com");

        store.saveUser(userId, "person@example.com", "argon-hash", now, now);
        assertThat(store.findUserByEmail("person@example.com").orElseThrow().id()).isEqualTo(userId);
        assertThat(new JdbcTemplate(database).queryForObject(
                "select plan_code from user_entitlements where user_id = ?", String.class, userId))
                .isEqualTo("free");

        store.saveSession("token-digest", userId, now, now, now.plusSeconds(3600));
        assertThat(store.findSession("token-digest").orElseThrow().userId()).isEqualTo(userId);
    }

    @Test
    void consumesChallengeOnlyOnce() {
        var challengeId = UUID.randomUUID();
        var now = Instant.parse("2026-08-06T08:00:00Z");
        store.saveChallenge(challengeId, "person@example.com", new byte[] {1}, now.plusSeconds(600), now);

        assertThat(store.consumeChallenge(challengeId, now)).isTrue();
        assertThat(store.consumeChallenge(challengeId, now)).isFalse();
    }

    @Test
    void usesTheLegacyUserIdentityAndRejectsDuplicateEmailWithoutServerError() {
        var userId = UUID.randomUUID();
        var now = Instant.parse("2026-08-06T08:00:00Z");

        assertThat(store.saveUser(userId, "person@example.com", "bcrypt-hash", now, now)).isTrue();
        assertThat(store.findUserByEmail("person@example.com").orElseThrow().id()).isEqualTo(userId);
        assertThat(new JdbcTemplate(database).queryForObject(
                "select id from \"user\" where username = ?", UUID.class, "person@example.com"))
                .isEqualTo(userId);
        assertThat(store.saveUser(UUID.randomUUID(), "person@example.com", "other-hash", now, now)).isFalse();
    }

    @Test
    void findsAnExistingLegacyUserBeforeTheGovernanceProjection() {
        var userId = UUID.randomUUID();
        var now = Instant.parse("2026-08-06T08:00:00Z");
        new JdbcTemplate(database).update(
                "insert into \"user\" (id, username, password_hash, created_at, updated_at) values (?, ?, ?, ?, ?)",
                userId, "legacy@example.com", "bcrypt-hash", now, now);

        assertThat(store.findUserByEmail("legacy@example.com").orElseThrow().id()).isEqualTo(userId);
        store.ensureGovernance(store.findUserByEmail("legacy@example.com").orElseThrow(), now);
        assertThat(new JdbcTemplate(database).queryForObject(
                "select count(*) from app_users where id = ?", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void prefersTheGovernanceIdentityWhenLegacyAndEmailRecordsShareAnAddress() {
        var legacyId = UUID.randomUUID();
        var governanceId = UUID.randomUUID();
        var now = Instant.parse("2026-08-06T08:00:00Z");
        var jdbc = new JdbcTemplate(database);
        jdbc.update(
                "insert into \"user\" (id, username, password_hash, created_at, updated_at) values (?, ?, ?, ?, ?)",
                legacyId, "person@example.com", "legacy-hash", now, now);
        jdbc.update(
                "insert into app_users (id, email, password_hash, created_at) values (?, ?, ?, ?)",
                governanceId, "person@example.com", "governance-hash", now);

        assertThat(store.findUserByEmail("person@example.com").orElseThrow().id()).isEqualTo(governanceId);
    }

    @Test
    void resetsBothPasswordIdentitiesAndRevokesSessionsForTheEmail() {
        var legacyId = UUID.randomUUID();
        var governanceId = UUID.randomUUID();
        var now = Instant.parse("2026-08-06T08:00:00Z");
        var jdbc = new JdbcTemplate(database);
        jdbc.update(
                "insert into \"user\" (id, username, password_hash, auth_version, created_at, updated_at) "
                        + "values (?, ?, ?, 4, ?, ?)",
                legacyId, "person@example.com", "legacy-hash", now, now);
        jdbc.update(
                "insert into app_users (id, email, password_hash, created_at) values (?, ?, ?, ?)",
                governanceId, "person@example.com", "governance-hash", now);
        jdbc.update(
                "insert into user_sessions (token_digest, user_id, created_at, last_seen_at, expires_at) "
                        + "values (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)",
                "governance-session", governanceId, now, now, now.plusSeconds(3600),
                "legacy-session", legacyId, now, now, now.plusSeconds(3600));

        store.updatePassword("person@example.com", "new-hash", now.plusSeconds(30));
        store.revokeSessionsByEmail("person@example.com", now.plusSeconds(30));

        assertThat(jdbc.queryForObject(
                "select password_hash from app_users where id = ?", String.class, governanceId))
                .isEqualTo("new-hash");
        assertThat(jdbc.queryForObject(
                "select password_hash from \"user\" where id = ?", String.class, legacyId))
                .isEqualTo("new-hash");
        assertThat(jdbc.queryForObject(
                "select auth_version from \"user\" where id = ?", Long.class, legacyId))
                .isEqualTo(5L);
        assertThat(jdbc.queryForObject(
                "select count(*) from user_sessions where revoked_at is not null", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void passwordUpdatesParticipateInTheCallingTransaction() {
        var userId = UUID.randomUUID();
        var now = Instant.parse("2026-08-06T08:00:00Z");
        var jdbc = new JdbcTemplate(database);
        jdbc.update(
                "insert into \"user\" (id, username, password_hash, created_at, updated_at) values (?, ?, ?, ?, ?)",
                userId, "person@example.com", "old-hash", now, now);
        jdbc.update(
                "insert into app_users (id, email, password_hash, created_at) values (?, ?, ?, ?)",
                userId, "person@example.com", "old-hash", now);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(database));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            store.updatePassword("person@example.com", "new-hash", now.plusSeconds(30));
            throw new IllegalStateException("force rollback after password update");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                "select password_hash from app_users where id = ?", String.class, userId))
                .isEqualTo("old-hash");
        assertThat(jdbc.queryForObject(
                "select password_hash from \"user\" where id = ?", String.class, userId))
                .isEqualTo("old-hash");
    }
}
