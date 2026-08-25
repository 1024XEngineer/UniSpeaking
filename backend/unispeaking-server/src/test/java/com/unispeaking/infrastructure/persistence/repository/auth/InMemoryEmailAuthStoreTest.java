package com.unispeaking.infrastructure.persistence.repository.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryEmailAuthStoreTest {
    @Test
    void coversChallengeUserPasswordAndSessionLifecycle() {
        var store = new InMemoryEmailAuthStore();
        UUID challengeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        byte[] digest = {1, 2};
        store.saveChallenge(challengeId, "a@example.com", digest, now.plusSeconds(60), now);
        digest[0] = 9;
        assertArrayEquals(new byte[] {1, 2}, store.findChallenge(challengeId).orElseThrow().codeDigest());
        assertFalse(store.findChallenge(UUID.randomUUID()).isPresent());
        assertTrue(store.consumeChallenge(challengeId, now));
        assertFalse(store.consumeChallenge(challengeId, now));
        assertFalse(store.consumeChallenge(UUID.randomUUID(), now));

        assertTrue(store.saveUser(userId, "a@example.com", "old", "A", now, now));
        assertFalse(store.saveUser(otherId, "a@example.com", "other", "B", now, now));
        assertTrue(store.findUserByEmail("a@example.com").isPresent());
        assertTrue(store.findUserById(userId).isPresent());
        store.updatePassword("missing@example.com", "new", now);
        store.updatePassword("a@example.com", "new", now);
        assertEquals("new", store.findUserById(userId).orElseThrow().passwordHash());

        store.saveSession("one", userId, now, now, now.plusSeconds(60));
        store.saveSession("two", otherId, now, now, now.plusSeconds(60));
        store.revokeSessionsByEmail("missing@example.com", now);
        store.revokeSessionsByEmail("a@example.com", now);
        assertEquals(now, store.findSession("one").orElseThrow().revokedAt());
        assertEquals(null, store.findSession("two").orElseThrow().revokedAt());
        store.revokeSession("missing");
        store.revokeSession("two");
		assertTrue(store.findSession("two").orElseThrow().revokedAt() != null);
    }
}
