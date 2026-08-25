package com.unispeaking.admin.auth.adapters.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.admin.auth.domain.AdminSession;
import java.time.Instant;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcAdminSessionRepositoryTest {
    @Test
    void persistsFindsTouchesAndRevokesSessions() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:admin-sessions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("create table admin_sessions (token_hash varchar primary key, admin_id uuid, created_at timestamp, last_seen_at timestamp, expires_at timestamp, revoked boolean)");
        var repository = new JdbcAdminSessionRepository(jdbc);
        UUID adminId = UUID.randomUUID();
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        repository.save(new AdminSession("one", adminId, at, at, at.plusSeconds(3600), false));

        assertFalse(repository.findByTokenHash("missing").isPresent());
        assertEquals(adminId, repository.findByTokenHash("one").orElseThrow().adminId());
        repository.touch("one", at.plusSeconds(10));
        assertEquals(at.plusSeconds(10), repository.findByTokenHash("one").orElseThrow().lastSeenAt());
        repository.revoke("one");
        assertTrue(repository.findByTokenHash("one").orElseThrow().revoked());
        repository.revokeAll(adminId);
    }
}
