package com.unispeaking.admin.auth.adapters.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.unispeaking.admin.auth.domain.AdminAccount;
import com.unispeaking.admin.auth.domain.AdminRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class JdbcAdminRepositoryTest {
    private JdbcAdminIdentityRepository identities;
    private JdbcAdminSessionRepository sessions;

    @BeforeEach
    void setUp() {
        var database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .addScript("db/admin-test-schema.sql")
                .build();
        var jdbc = new JdbcTemplate(database);
        identities = new JdbcAdminIdentityRepository(jdbc);
        sessions = new JdbcAdminSessionRepository(jdbc);
    }

    @Test
    void persistsAdminIdentityAndSession() {
        var id = UUID.randomUUID();
        var now = Instant.parse("2026-08-06T08:00:00Z");
        var account = new AdminAccount(id, "admin@example.com", "hash", AdminRole.SUPER_ADMIN, true);
        identities.save(account, now);
        assertThat(identities.findByLogin("admin@example.com")).contains(account);

        var session = new com.unispeaking.admin.auth.domain.AdminSession(
                "token-hash", id, now, now, now.plusSeconds(3600), false);
        sessions.save(session);
        assertThat(sessions.findByTokenHash("token-hash")).contains(session);
    }

    @Test
    void bootstrapRefreshesPasswordWithoutChangingAdminIdentity() {
        var id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var now = Instant.parse("2026-08-06T08:00:00Z");
        identities.saveOrUpdateBootstrap(
                new AdminAccount(id, "admin@example.com", "old-hash", AdminRole.SUPER_ADMIN, true), now);
        identities.saveOrUpdateBootstrap(
                new AdminAccount(id, "admin@example.com", "new-hash", AdminRole.SUPER_ADMIN, true), now.plusSeconds(1));

        var refreshed = identities.findById(id).orElseThrow();
        assertThat(refreshed.passwordHash()).isEqualTo("new-hash");
        assertThat(refreshed.login()).isEqualTo("admin@example.com");
    }
}
