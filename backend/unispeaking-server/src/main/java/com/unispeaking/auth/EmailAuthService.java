package com.unispeaking.auth;

import com.unispeaking.infrastructure.email.VerificationEmailSender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class EmailAuthService {

    private static final int CODE_TTL_SECONDS = 600;
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationEmailSender emailSender;
    private final HumanVerificationGateway humanVerificationGateway;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration challengeTtl;
    private final Duration sessionTtl;
    private final EmailAuthStore store;

    @Autowired
    public EmailAuthService(
            VerificationEmailSender emailSender,
            HumanVerificationGateway humanVerificationGateway,
            @Qualifier("userPasswordEncoder") PasswordEncoder passwordEncoder,
            Clock clock,
            @Qualifier("userAuthChallengeTtl") Duration challengeTtl,
            @Qualifier("userAuthSessionTtl") Duration sessionTtl,
            EmailAuthStore store) {
        this.emailSender = emailSender;
        this.humanVerificationGateway = humanVerificationGateway;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.challengeTtl = challengeTtl;
        this.sessionTtl = sessionTtl;
        this.store = store;
    }

    public EmailAuthService(
            VerificationEmailSender emailSender,
            HumanVerificationGateway humanVerificationGateway,
                PasswordEncoder passwordEncoder,
                Clock clock,
                Duration challengeTtl) {
        this(emailSender, humanVerificationGateway, passwordEncoder, clock, challengeTtl,
                Duration.ofHours(8), new InMemoryEmailAuthStore());
    }

    public ChallengeIssued issueChallenge(String rawEmail, String humanVerificationToken) {
        if (!humanVerificationGateway.verify(humanVerificationToken)) {
            throw new AuthException("HUMAN_VERIFICATION_REQUIRED");
        }
        var email = normalizeEmail(rawEmail);
        var code = String.format("%0" + CODE_LENGTH + "d", RANDOM.nextInt(1_000_000));
        var challengeId = UUID.randomUUID();
        store.saveChallenge(challengeId, email, digest(code), clock.instant().plus(challengeTtl), clock.instant());
        emailSender.sendVerificationCode(email, code, CODE_TTL_SECONDS);
        return new ChallengeIssued(challengeId, CODE_TTL_SECONDS, 60);
    }

    public UserView register(String rawEmail, String rawPassword, UUID challengeId, String code) {
        var email = normalizeEmail(rawEmail);
        if (!StringUtils.hasText(rawPassword) || rawPassword.length() < 12) {
            throw new AuthException("WEAK_PASSWORD");
        }
        var challenge = store.findChallenge(challengeId).orElse(null);
        var now = clock.instant();
        if (challenge == null || challenge.consumed() || challenge.expiresAt().isBefore(now)
                || !challenge.email().equals(email) || !MessageDigest.isEqual(challenge.codeDigest(), digest(code))) {
            throw new AuthException("CHALLENGE_INVALID");
        }
        if (!store.consumeChallenge(challengeId, now)) {
            throw new AuthException("CHALLENGE_INVALID");
        }
        var userId = UUID.randomUUID();
        if (!store.saveUser(userId, email, passwordEncoder.encode(rawPassword), now, now)) {
            throw new AuthException("IDENTITY_ALREADY_BOUND");
        }
        return new UserView(userId, email);
    }

    public LoginResult login(String rawEmail, String password) {
        var user = store.findUserByEmail(normalizeEmail(rawEmail)).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new AuthException("INVALID_CREDENTIALS");
        }
        var token = randomToken();
        var now = clock.instant();
        store.ensureGovernance(user, now);
        store.saveSession(digestString(token), user.id(), now, now, now.plus(sessionTtl));
        return new LoginResult(token, new UserView(user.id(), user.email()));
    }

    @Transactional
    public void resetPassword(String rawEmail, String rawPassword, UUID challengeId, String code) {
        var email = normalizeEmail(rawEmail);
        if (!StringUtils.hasText(rawPassword) || rawPassword.length() < 12 || rawPassword.length() > 200) {
            throw new AuthException("WEAK_PASSWORD");
        }
        var challenge = store.findChallenge(challengeId).orElse(null);
        var now = clock.instant();
        if (challenge == null || challenge.consumed() || challenge.expiresAt().isBefore(now)
                || !challenge.email().equals(email) || !MessageDigest.isEqual(challenge.codeDigest(), digest(code))) {
            throw new AuthException("CHALLENGE_INVALID");
        }
        if (!store.consumeChallenge(challengeId, now)) {
            throw new AuthException("CHALLENGE_INVALID");
        }
        if (store.findUserByEmail(email).isEmpty()) {
            throw new AuthException("IDENTITY_NOT_FOUND");
        }
        store.updatePassword(email, passwordEncoder.encode(rawPassword), now);
        store.revokeSessionsByEmail(email, now);
    }

    public UserView currentUser(String rawToken) {
        var session = store.findSession(digestString(rawToken)).orElse(null);
        if (session == null || !session.activeAt(clock.instant())) {
            throw new AuthException("UNAUTHENTICATED");
        }
        var user = store.findUserById(session.userId()).orElse(null);
        if (user == null) {
            throw new AuthException("UNAUTHENTICATED");
        }
        return new UserView(user.id(), user.email());
    }

    public void logout(String rawToken) {
        store.revokeSession(digestString(rawToken));
    }

    private static String normalizeEmail(String rawEmail) {
        if (!StringUtils.hasText(rawEmail)) {
            throw new AuthException("INVALID_EMAIL");
        }
        var email = rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            throw new AuthException("INVALID_EMAIL");
        }
        return email;
    }

    private static byte[] digest(String value) {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String digestString(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(value));
    }

    private static String randomToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record ChallengeIssued(UUID challengeId, int expiresInSeconds, int resendAfterSeconds) {
    }

    public record UserView(UUID id, String email) {
    }

    public record LoginResult(String rawToken, UserView user) {
    }

    public static final class AuthException extends RuntimeException {
        public AuthException(String code) {
            super(code);
        }
    }
}
