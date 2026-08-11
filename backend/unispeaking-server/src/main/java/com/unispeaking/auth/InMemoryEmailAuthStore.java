package com.unispeaking.auth;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Test-only fallback. Production uses JdbcEmailAuthStore. */
final class InMemoryEmailAuthStore implements EmailAuthStore {
    private final Map<UUID, ChallengeRecord> challenges = new ConcurrentHashMap<>();
    private final Map<String, UserRecord> usersByEmail = new ConcurrentHashMap<>();
    private final Map<UUID, UserRecord> usersById = new ConcurrentHashMap<>();
    private final Map<String, SessionRecord> sessions = new ConcurrentHashMap<>();

    @Override
    public void saveChallenge(UUID id, String email, byte[] codeDigest, Instant expiresAt, Instant createdAt) {
        challenges.put(id, new ChallengeRecord(id, email, codeDigest.clone(), expiresAt, null));
    }

    @Override
    public Optional<ChallengeRecord> findChallenge(UUID id) {
        return Optional.ofNullable(challenges.get(id));
    }

    @Override
    public synchronized boolean consumeChallenge(UUID id, Instant consumedAt) {
        var current = challenges.get(id);
        if (current == null || current.consumed()) {
            return false;
        }
        challenges.put(id, new ChallengeRecord(current.id(), current.email(), current.codeDigest(), current.expiresAt(), consumedAt));
        return true;
    }

    @Override
    public boolean saveUser(UUID id, String email, String passwordHash, Instant createdAt, Instant emailVerifiedAt) {
        var user = new UserRecord(id, email, passwordHash);
        if (usersByEmail.putIfAbsent(email, user) != null) {
            return false;
        }
        usersById.put(id, user);
        return true;
    }

    @Override
    public Optional<UserRecord> findUserByEmail(String email) {
        return Optional.ofNullable(usersByEmail.get(email));
    }

    @Override
    public Optional<UserRecord> findUserById(UUID id) {
        return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public void saveSession(String tokenDigest, UUID userId, Instant createdAt, Instant lastSeenAt, Instant expiresAt) {
        sessions.put(tokenDigest, new SessionRecord(tokenDigest, userId, createdAt, lastSeenAt, expiresAt, null));
    }

    @Override
    public Optional<SessionRecord> findSession(String tokenDigest) {
        return Optional.ofNullable(sessions.get(tokenDigest));
    }

    @Override
    public synchronized void revokeSession(String tokenDigest) {
        var session = sessions.get(tokenDigest);
        if (session != null) {
            sessions.put(tokenDigest, new SessionRecord(session.tokenDigest(), session.userId(), session.createdAt(),
                    session.lastSeenAt(), session.expiresAt(), Instant.now()));
        }
    }
}
