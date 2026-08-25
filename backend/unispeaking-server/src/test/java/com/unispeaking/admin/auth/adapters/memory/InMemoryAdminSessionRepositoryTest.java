package com.unispeaking.admin.auth.adapters.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.admin.auth.domain.AdminSession;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryAdminSessionRepositoryTest {
    @Test
    void savesTouchesAndRevokesIndividualAndMatchingAdminSessions() {
        var repository = new InMemoryAdminSessionRepository();
        UUID firstAdmin = UUID.randomUUID();
        UUID secondAdmin = UUID.randomUUID();
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        repository.save(session("one", firstAdmin, created));
        repository.save(session("two", secondAdmin, created));

        assertEquals(2, repository.size());
        assertFalse(repository.findByTokenHash("missing").isPresent());
        repository.touch("missing", created.plusSeconds(1));
        repository.touch("one", created.plusSeconds(2));
        assertEquals(created.plusSeconds(2), repository.findByTokenHash("one").orElseThrow().lastSeenAt());
        repository.revoke("missing");
        repository.revoke("one");
        assertTrue(repository.findByTokenHash("one").orElseThrow().revoked());

        repository.revokeAll(secondAdmin);
        assertTrue(repository.findByTokenHash("two").orElseThrow().revoked());
        repository.revokeAll(UUID.randomUUID());
    }

    private AdminSession session(String token, UUID adminId, Instant at) {
        return new AdminSession(token, adminId, at, at, at.plusSeconds(3600), false);
    }
}
