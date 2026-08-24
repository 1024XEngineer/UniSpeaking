package com.unispeaking.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unispeaking.common.email.VerificationEmailSender;
import com.unispeaking.common.exception.EmailAuthException;
import com.unispeaking.infrastructure.persistence.repository.auth.InMemoryEmailAuthStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class EmailAuthServiceTest {

    private CapturingEmailSender emailSender;
    private EmailAuthService service;

    @BeforeEach
    void setUp() {
        emailSender = new CapturingEmailSender();
        service = new EmailAuthService(
                emailSender,
                token -> "verified-human".equals(token),
                Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                Clock.fixed(Instant.parse("2026-08-06T08:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(10),
                new InMemoryEmailAuthStore());
    }

    @Test
    void registrationConsumesChallengeAndPasswordLoginCreatesSession() {
        var challenge = service.issueChallenge(" Person@Example.com ", "verified-human");

        var user = service.register(
                "person@example.com", "correct-horse-battery-staple", challenge.challengeId(),
                emailSender.lastCode());

        assertThat(user.email()).isEqualTo("person@example.com");
        var login = service.login("PERSON@example.com", "correct-horse-battery-staple");
        assertThat(service.currentUser(login.rawToken()).email()).isEqualTo("person@example.com");

        assertThatThrownBy(() -> service.register(
                "person@example.com", "another-password", challenge.challengeId(),
                emailSender.lastCode()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("CHALLENGE_INVALID");
    }

    @Test
    void mobileChallengeNormalizesEmailAndRegistrationTrimsNickname() {
        var challenge = service.issueMobileChallenge(" Person@Example.com ");

        var user = service.register(
                "PERSON@example.com", "correct-horse-battery-staple", challenge.challengeId(),
                emailSender.lastCode(), "  Sunny  ");

        assertThat(user.email()).isEqualTo("person@example.com");
    }

    @Test
    void registrationRejectsWeakPasswordsBeforeReadingChallenge() {
        for (String password : Arrays.asList(null, "", "too-short")) {
            assertThatThrownBy(() -> service.register(
                    "person@example.com", password, UUID.randomUUID(), "123456"))
                    .isInstanceOf(EmailAuthException.class)
                    .hasMessage("WEAK_PASSWORD");
        }
    }

    @Test
    void registrationRejectsMissingExpiredMismatchedAndIncorrectChallenges() {
        var challenge = service.issueChallenge("person@example.com", "verified-human");

        assertThatThrownBy(() -> service.register(
                "person@example.com", "correct-password", UUID.randomUUID(), emailSender.lastCode()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("CHALLENGE_INVALID");
        assertThatThrownBy(() -> service.register(
                "other@example.com", "correct-password", challenge.challengeId(), emailSender.lastCode()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("CHALLENGE_INVALID");
        assertThatThrownBy(() -> service.register(
                "person@example.com", "correct-password", challenge.challengeId(), "000000"))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("CHALLENGE_INVALID");

        var expiredService = newService(Duration.ofSeconds(-1), Duration.ofHours(8), new InMemoryEmailAuthStore(), null);
        var expired = expiredService.issueMobileChallenge("person@example.com");
        assertThatThrownBy(() -> expiredService.register(
                "person@example.com", "correct-password", expired.challengeId(), emailSender.lastCode()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("CHALLENGE_INVALID");
    }

    @Test
    void registrationMapsAChallengeConsumptionRaceAndDuplicateIdentity() {
        var store = Mockito.mock(EmailAuthStore.class);
        var challengeId = UUID.randomUUID();
        var now = Instant.parse("2026-08-06T08:00:00Z");
        var code = "123456";
        var challenge = validChallenge(challengeId, "person@example.com", code, now);
        Mockito.when(store.findChallenge(challengeId)).thenReturn(Optional.of(challenge));
        Mockito.when(store.consumeChallenge(challengeId, now)).thenReturn(false);
        var mockedStoreService = newService(Duration.ofMinutes(10), Duration.ofHours(8), store, null);

        assertThatThrownBy(() -> mockedStoreService.register(
                "person@example.com", "correct-password", challengeId, code))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("CHALLENGE_INVALID");

        var firstChallenge = service.issueChallenge("person@example.com", "verified-human");
        service.register("person@example.com", "correct-password", firstChallenge.challengeId(), emailSender.lastCode());
        var duplicateChallenge = service.issueChallenge("person@example.com", "verified-human");
        assertThatThrownBy(() -> service.register(
                "person@example.com", "another-password", duplicateChallenge.challengeId(), emailSender.lastCode()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("IDENTITY_ALREADY_BOUND");
    }

    @Test
    void incorrectPasswordDoesNotCreateSession() {
        var challenge = service.issueChallenge("person@example.com", "verified-human");
        service.register("person@example.com", "correct-password", challenge.challengeId(), emailSender.lastCode());

        assertThatThrownBy(() -> service.login("person@example.com", "wrong-password"))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("INVALID_CREDENTIALS");
    }

    @Test
    void loginRejectsAnUnknownEmail() {
        assertThatThrownBy(() -> service.login("unknown@example.com", "correct-password"))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("INVALID_CREDENTIALS");
    }

    @Test
    void humanVerificationIsRequiredBeforePasswordLoginCreatesSession() {
        var challenge = service.issueChallenge("person@example.com", "verified-human");
        service.register("person@example.com", "correct-password", challenge.challengeId(), emailSender.lastCode());

        assertThatThrownBy(() -> service.login("person@example.com", "correct-password", "invalid"))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("HUMAN_VERIFICATION_REQUIRED");
    }

    @Test
    void rejectsChallengeBeforeEmailDeliveryWhenHumanVerificationFails() {
        assertThatThrownBy(() -> service.issueChallenge("person@example.com", "invalid"))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("HUMAN_VERIFICATION_REQUIRED");
        assertThat(emailSender.codes).isEmpty();
    }

    @Test
    void resetsPasswordWithEmailChallengeAndRevokesExistingSessions() {
        var registrationChallenge = service.issueChallenge("person@example.com", "verified-human");
        service.register(
                "person@example.com", "correct-old-password", registrationChallenge.challengeId(),
                emailSender.lastCode());
        var oldLogin = service.login("person@example.com", "correct-old-password");

        var resetChallenge = service.issueChallenge("person@example.com", "verified-human");
        service.resetPassword(
                "person@example.com", "correct-new-password", resetChallenge.challengeId(),
                emailSender.lastCode());

        assertThatThrownBy(() -> service.currentUser(oldLogin.rawToken()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("UNAUTHENTICATED");
        assertThatThrownBy(() -> service.login("person@example.com", "correct-old-password"))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("INVALID_CREDENTIALS");
        assertThat(service.login("person@example.com", "correct-new-password").user().email())
                .isEqualTo("person@example.com");
        assertThatThrownBy(() -> service.resetPassword(
                "person@example.com", "another-new-password", resetChallenge.challengeId(),
                emailSender.lastCode()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("CHALLENGE_INVALID");
    }

    @Test
    void resetPasswordRejectsPasswordBoundsAndUnknownIdentity() {
        for (String password : Arrays.asList(null, "", "too-short", "a".repeat(201))) {
            var challenge = service.issueChallenge("person@example.com", "verified-human");
            assertThatThrownBy(() -> service.resetPassword(
                    "person@example.com", password, challenge.challengeId(), emailSender.lastCode()))
                    .isInstanceOf(EmailAuthException.class)
                    .hasMessage("WEAK_PASSWORD");
        }

        var challenge = service.issueChallenge("person@example.com", "verified-human");
        assertThatThrownBy(() -> service.resetPassword(
                "person@example.com", "correct-new-password", challenge.challengeId(), emailSender.lastCode()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("IDENTITY_NOT_FOUND");
    }

    @Test
    void resetPasswordRejectsExpiredAndMismatchedChallenges() {
        var expiredService = newService(Duration.ofSeconds(-1), Duration.ofHours(8), new InMemoryEmailAuthStore(), null);
        var expired = expiredService.issueMobileChallenge("person@example.com");
        assertThatThrownBy(() -> expiredService.resetPassword(
                "person@example.com", "correct-new-password", expired.challengeId(), emailSender.lastCode()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("CHALLENGE_INVALID");

        var challenge = service.issueChallenge("person@example.com", "verified-human");
        assertThatThrownBy(() -> service.resetPassword(
                "other@example.com", "correct-new-password", challenge.challengeId(), emailSender.lastCode()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("CHALLENGE_INVALID");
        assertThatThrownBy(() -> service.resetPassword(
                "person@example.com", "correct-new-password", challenge.challengeId(), "000000"))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("CHALLENGE_INVALID");
    }

    @Test
    void resetPasswordRevokesRefreshTokensWhenConfigured() {
        var store = Mockito.mock(EmailAuthStore.class);
        var refreshTokens = Mockito.mock(RefreshTokenService.class);
        var challengeId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var now = Instant.parse("2026-08-06T08:00:00Z");
        var challenge = validChallenge(challengeId, "person@example.com", "123456", now);
        var user = new EmailAuthStore.UserRecord(userId, "person@example.com", "old-hash");
        Mockito.when(store.findChallenge(challengeId)).thenReturn(Optional.of(challenge));
        Mockito.when(store.consumeChallenge(challengeId, now)).thenReturn(true);
        Mockito.when(store.findUserByEmail("person@example.com")).thenReturn(Optional.of(user));
        var configuredService = newService(Duration.ofMinutes(10), Duration.ofHours(8), store, refreshTokens);

        configuredService.resetPassword("PERSON@example.com", "correct-new-password", challengeId, "123456");

        Mockito.verify(refreshTokens).revokeAll(userId);
    }

    @Test
    void currentUserRejectsMissingExpiredRevokedAndDeletedSessions() {
        assertThatThrownBy(() -> service.currentUser("missing-token"))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("UNAUTHENTICATED");

        var challenge = service.issueChallenge("person@example.com", "verified-human");
        service.register("person@example.com", "correct-password", challenge.challengeId(), emailSender.lastCode());
        var login = service.login("person@example.com", "correct-password");
        service.logout(login.rawToken());
        assertThatThrownBy(() -> service.currentUser(login.rawToken()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("UNAUTHENTICATED");

        var expiredService = newService(Duration.ofMinutes(10), Duration.ofSeconds(-1),
                new InMemoryEmailAuthStore(), null);
        var expiredChallenge = expiredService.issueMobileChallenge("expired@example.com");
        expiredService.register("expired@example.com", "correct-password", expiredChallenge.challengeId(), emailSender.lastCode());
        var expiredLogin = expiredService.login("expired@example.com", "correct-password");
        assertThatThrownBy(() -> expiredService.currentUser(expiredLogin.rawToken()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("UNAUTHENTICATED");

        var store = Mockito.mock(EmailAuthStore.class);
        var userId = UUID.randomUUID();
        Mockito.when(store.findSession(Mockito.anyString())).thenReturn(Optional.of(new EmailAuthStore.SessionRecord(
                "digest", userId, Instant.parse("2026-08-06T07:00:00Z"),
                Instant.parse("2026-08-06T07:00:00Z"), Instant.parse("2026-08-06T09:00:00Z"), null)));
        Mockito.when(store.findUserById(userId)).thenReturn(Optional.empty());
        var deletedUserService = newService(Duration.ofMinutes(10), Duration.ofHours(8), store, null);
        assertThatThrownBy(() -> deletedUserService.currentUser("some-token"))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("UNAUTHENTICATED");
    }

    @Test
    void rejectsBlankAndMalformedEmails() {
        for (String email : Arrays.asList(null, "   ", "invalid", "@example.com", "person@")) {
            assertThatThrownBy(() -> service.issueMobileChallenge(email))
                    .isInstanceOf(EmailAuthException.class)
                    .hasMessage("INVALID_EMAIL");
        }
    }

    private EmailAuthService newService(
            Duration challengeTtl, Duration sessionTtl, EmailAuthStore store, RefreshTokenService refreshTokens) {
        return new EmailAuthService(
                emailSender,
                token -> "verified-human".equals(token),
                Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                Clock.fixed(Instant.parse("2026-08-06T08:00:00Z"), ZoneOffset.UTC),
                challengeTtl,
                sessionTtl,
                store,
                refreshTokens);
    }

    private static EmailAuthStore.ChallengeRecord validChallenge(
            UUID id, String email, String code, Instant now) {
        try {
            return new EmailAuthStore.ChallengeRecord(
                    id, email,
                    MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)),
                    now.plusSeconds(600), null);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class CapturingEmailSender implements VerificationEmailSender {
        private final List<String> codes = new ArrayList<>();

        @Override
        public void sendVerificationCode(String recipient, String code, int ttlSeconds) {
            codes.add(code);
        }

        String lastCode() {
            return codes.get(codes.size() - 1);
        }
    }
}
