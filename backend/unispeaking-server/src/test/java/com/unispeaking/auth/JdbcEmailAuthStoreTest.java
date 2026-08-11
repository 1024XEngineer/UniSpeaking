package com.unispeaking.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

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
}
