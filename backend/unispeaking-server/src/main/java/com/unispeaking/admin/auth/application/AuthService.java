package com.unispeaking.admin.auth.application;

import com.unispeaking.admin.auth.domain.AdminSession;
import com.unispeaking.admin.auth.ports.AdminIdentityRepository;
import com.unispeaking.admin.auth.ports.AdminSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public record LoginResult(String rawToken, Instant expiresAt) {
    }

    public record CurrentAdmin(UUID id, String login, String role) {
    }

    private final AdminIdentityRepository identities;
    private final AdminSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final long idleSeconds;
    private final long absoluteSeconds;

    public AuthService(
            AdminIdentityRepository identities,
            AdminSessionRepository sessions,
            PasswordEncoder passwordEncoder,
            Clock clock,
            long idleSeconds,
            long absoluteSeconds) {
        this.identities = identities;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.idleSeconds = idleSeconds;
        this.absoluteSeconds = absoluteSeconds;
    }

    public LoginResult login(String login, String password) {
        var account = identities.findByLogin(login).orElseThrow(InvalidCredentialsException::new);
        if (!account.canAuthenticate() || !passwordEncoder.matches(password, account.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        var tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        var rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        var now = clock.instant();
        var expiresAt = now.plusSeconds(absoluteSeconds);
        sessions.save(new AdminSession(hash(rawToken), account.id(), now, now, expiresAt, false));
        return new LoginResult(rawToken, expiresAt);
    }

    public CurrentAdmin authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidSessionException();
        }
        var tokenHash = hash(rawToken);
        var session = sessions.findByTokenHash(tokenHash).orElseThrow(InvalidSessionException::new);
        var now = clock.instant();
        if (!session.activeAt(now, idleSeconds)) {
            sessions.revoke(tokenHash);
            throw new InvalidSessionException();
        }
        var account = identities.findById(session.adminId()).orElseThrow(InvalidSessionException::new);
        if (!account.canAuthenticate()) {
            sessions.revoke(tokenHash);
            throw new InvalidSessionException();
        }
        sessions.touch(tokenHash, now);
        return new CurrentAdmin(account.id(), account.login(), account.role().name());
    }

    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            sessions.revoke(hash(rawToken));
        }
    }

    public static String hash(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
