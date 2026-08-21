package com.unispeaking.service.auth;

import com.unispeaking.common.email.VerificationEmailSender;
import com.unispeaking.common.exception.EmailAuthException;
import com.unispeaking.common.security.HumanVerificationGateway;
import com.unispeaking.domain.dto.auth.EmailAuthChallenge;
import com.unispeaking.domain.dto.auth.EmailAuthUser;
import com.unispeaking.domain.dto.auth.EmailLoginResult;
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
    private final RefreshTokenService refreshTokenService;

    @Autowired
    public EmailAuthService(
            VerificationEmailSender emailSender,
            HumanVerificationGateway humanVerificationGateway,
            @Qualifier("userPasswordEncoder") PasswordEncoder passwordEncoder,
            Clock clock,
            @Qualifier("userAuthChallengeTtl") Duration challengeTtl,
            @Qualifier("userAuthSessionTtl") Duration sessionTtl,
            EmailAuthStore store,
            RefreshTokenService refreshTokenService) {
        this.emailSender = emailSender;
        this.humanVerificationGateway = humanVerificationGateway;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.challengeTtl = challengeTtl;
        this.sessionTtl = sessionTtl;
        this.store = store;
        this.refreshTokenService = refreshTokenService;
    }

    public EmailAuthService(
            VerificationEmailSender emailSender,
            HumanVerificationGateway humanVerificationGateway,
            PasswordEncoder passwordEncoder,
            Clock clock,
            Duration challengeTtl,
            EmailAuthStore store) {
        this(emailSender, humanVerificationGateway, passwordEncoder, clock, challengeTtl,
                Duration.ofHours(8), store, null);
    }

    public EmailAuthChallenge issueChallenge(String rawEmail, String humanVerificationToken) {
        if (!humanVerificationGateway.verify(humanVerificationToken)) {
            throw new EmailAuthException("HUMAN_VERIFICATION_REQUIRED");
        }
        return issueVerifiedChallenge(rawEmail);
    }

    /** Issues an email challenge for the mobile registration flow. */
    public EmailAuthChallenge issueMobileChallenge(String rawEmail) {
        return issueVerifiedChallenge(rawEmail);
    }

    private EmailAuthChallenge issueVerifiedChallenge(String rawEmail) {
        var email = normalizeEmail(rawEmail);
        var code = String.format("%0" + CODE_LENGTH + "d", RANDOM.nextInt(1_000_000));
        var challengeId = UUID.randomUUID();
        store.saveChallenge(challengeId, email, digest(code), clock.instant().plus(challengeTtl), clock.instant());
        emailSender.sendVerificationCode(email, code, CODE_TTL_SECONDS);
        return new EmailAuthChallenge(challengeId, CODE_TTL_SECONDS, 60);
    }

    public EmailAuthUser register(String rawEmail, String rawPassword, UUID challengeId, String code) {
        return register(rawEmail, rawPassword, challengeId, code, null);
    }

    public EmailAuthUser register(
            String rawEmail,
            String rawPassword,
            UUID challengeId,
            String code,
            String nickname) {
        var email = normalizeEmail(rawEmail);
        if (!StringUtils.hasText(rawPassword) || rawPassword.length() < 12) {
            throw new EmailAuthException("WEAK_PASSWORD");
        }
        var challenge = store.findChallenge(challengeId).orElse(null);
        var now = clock.instant();
        if (challenge == null || challenge.consumed() || challenge.expiresAt().isBefore(now)
                || !challenge.email().equals(email) || !MessageDigest.isEqual(challenge.codeDigest(), digest(code))) {
            throw new EmailAuthException("CHALLENGE_INVALID");
        }
        if (!store.consumeChallenge(challengeId, now)) {
            throw new EmailAuthException("CHALLENGE_INVALID");
        }
        var userId = UUID.randomUUID();
        var normalizedNickname = StringUtils.hasText(nickname) ? nickname.trim() : null;
        if (!store.saveUser(userId, email, passwordEncoder.encode(rawPassword), normalizedNickname, now, now)) {
            throw new EmailAuthException("IDENTITY_ALREADY_BOUND");
        }
        return new EmailAuthUser(userId, email);
    }

    public EmailLoginResult login(String rawEmail, String password) {
        var user = store.findUserByEmail(normalizeEmail(rawEmail)).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.passwordHash())) {
            throw new EmailAuthException("INVALID_CREDENTIALS");
        }
        var token = randomToken();
        var now = clock.instant();
        store.ensureGovernance(user, now);
        store.saveSession(digestString(token), user.id(), now, now, now.plus(sessionTtl));
        return new EmailLoginResult(token, new EmailAuthUser(user.id(), user.email()));
    }

    public EmailLoginResult login(String rawEmail, String password, String humanVerificationToken) {
        if (!humanVerificationGateway.verify(humanVerificationToken)) {
            throw new EmailAuthException("HUMAN_VERIFICATION_REQUIRED");
        }
        return login(rawEmail, password);
    }

    @Transactional
    public void resetPassword(String rawEmail, String rawPassword, UUID challengeId, String code) {
        var email = normalizeEmail(rawEmail);
        if (!StringUtils.hasText(rawPassword) || rawPassword.length() < 12 || rawPassword.length() > 200) {
            throw new EmailAuthException("WEAK_PASSWORD");
        }
        var challenge = store.findChallenge(challengeId).orElse(null);
        var now = clock.instant();
        if (challenge == null || challenge.consumed() || challenge.expiresAt().isBefore(now)
                || !challenge.email().equals(email) || !MessageDigest.isEqual(challenge.codeDigest(), digest(code))) {
            throw new EmailAuthException("CHALLENGE_INVALID");
        }
        if (!store.consumeChallenge(challengeId, now)) {
            throw new EmailAuthException("CHALLENGE_INVALID");
        }
        if (store.findUserByEmail(email).isEmpty()) {
            throw new EmailAuthException("IDENTITY_NOT_FOUND");
        }
        store.updatePassword(email, passwordEncoder.encode(rawPassword), now);
        store.revokeSessionsByEmail(email, now);
        if (refreshTokenService != null) {
            store.findUserByEmail(email).ifPresent(user -> refreshTokenService.revokeAll(user.id()));
        }
    }

    public EmailAuthUser currentUser(String rawToken) {
        var session = store.findSession(digestString(rawToken)).orElse(null);
        if (session == null || !session.activeAt(clock.instant())) {
            throw new EmailAuthException("UNAUTHENTICATED");
        }
        var user = store.findUserById(session.userId()).orElse(null);
        if (user == null) {
            throw new EmailAuthException("UNAUTHENTICATED");
        }
        return new EmailAuthUser(user.id(), user.email());
    }

    public void logout(String rawToken) {
        store.revokeSession(digestString(rawToken));
    }

    private static String normalizeEmail(String rawEmail) {
        if (!StringUtils.hasText(rawEmail)) {
            throw new EmailAuthException("INVALID_EMAIL");
        }
        var email = rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            throw new EmailAuthException("INVALID_EMAIL");
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

}
