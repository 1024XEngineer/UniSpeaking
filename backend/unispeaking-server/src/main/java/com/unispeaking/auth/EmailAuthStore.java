package com.unispeaking.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for email identity, verification challenges, and sessions. */
public interface EmailAuthStore {
    void saveChallenge(UUID id, String email, byte[] codeDigest, Instant expiresAt, Instant createdAt);

    Optional<ChallengeRecord> findChallenge(UUID id);

    boolean consumeChallenge(UUID id, Instant consumedAt);

    boolean saveUser(UUID id, String email, String passwordHash, Instant createdAt, Instant emailVerifiedAt);

    Optional<UserRecord> findUserByEmail(String email);

    Optional<UserRecord> findUserById(UUID id);

    void updatePassword(String email, String passwordHash, Instant updatedAt);

    void revokeSessionsByEmail(String email, Instant revokedAt);

    /** Ensures that a legacy business user is visible to governance queries. */
    default void ensureGovernance(UserRecord user, Instant now) {
    }

    void saveSession(String tokenDigest, UUID userId, Instant createdAt, Instant lastSeenAt, Instant expiresAt);

    Optional<SessionRecord> findSession(String tokenDigest);

    void revokeSession(String tokenDigest);

    record ChallengeRecord(UUID id, String email, byte[] codeDigest, Instant expiresAt, Instant consumedAt) {
        public boolean consumed() {
            return consumedAt != null;
        }
    }

    record UserRecord(UUID id, String email, String passwordHash) {
    }

    record SessionRecord(String tokenDigest, UUID userId, Instant createdAt, Instant lastSeenAt, Instant expiresAt,
            Instant revokedAt) {
        public boolean activeAt(Instant now) {
            return revokedAt == null && expiresAt.isAfter(now);
        }
    }
}
