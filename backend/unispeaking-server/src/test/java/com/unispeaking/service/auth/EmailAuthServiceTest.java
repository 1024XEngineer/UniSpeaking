package com.unispeaking.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unispeaking.common.email.VerificationEmailSender;
import com.unispeaking.common.exception.EmailAuthException;
import com.unispeaking.infrastructure.persistence.repository.auth.InMemoryEmailAuthStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class EmailAuthServiceTest {

    private CapturingEmailSender emailSender;
    private InMemoryEmailAuthStore store;
    private EmailAuthService service;

    @BeforeEach
    void setUp() {
        emailSender = new CapturingEmailSender();
        store = new InMemoryEmailAuthStore();
        service = serviceAt(Instant.parse("2026-08-06T08:00:00Z"));
    }

    private EmailAuthService serviceAt(Instant instant) {
        return new EmailAuthService(
                emailSender,
                token -> "verified-human".equals(token),
                Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                Clock.fixed(instant, ZoneOffset.UTC),
                Duration.ofMinutes(10),
                Duration.ofHours(8),
                Duration.ofDays(30),
                store);
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
    void incorrectPasswordDoesNotCreateSession() {
        var challenge = service.issueChallenge("person@example.com", "verified-human");
        service.register("person@example.com", "correct-password", challenge.challengeId(), emailSender.lastCode());

        assertThatThrownBy(() -> service.login("person@example.com", "wrong-password"))
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
    void mobileSessionRemainsValidWhileItIsRefreshedWithinTheIdleWindow() {
        var challenge = service.issueChallenge("person@example.com", "verified-human");
        service.register("person@example.com", "correct-password", challenge.challengeId(), emailSender.lastCode());
        var login = service.loginMobile("person@example.com", "correct-password");

        var day29 = serviceAt(Instant.parse("2026-09-04T08:00:00Z"));
        assertThat(day29.refreshMobileSession(login.rawToken()).email()).isEqualTo("person@example.com");

        var day58 = serviceAt(Instant.parse("2026-10-03T08:00:00Z"));
        assertThat(day58.currentUser(login.rawToken()).email()).isEqualTo("person@example.com");
    }

    @Test
    void mobileSessionCannotRefreshAfterItsIdleWindowOrRevocation() {
        var challenge = service.issueChallenge("person@example.com", "verified-human");
        service.register("person@example.com", "correct-password", challenge.challengeId(), emailSender.lastCode());
        var expired = service.loginMobile("person@example.com", "correct-password");

        assertThatThrownBy(() -> serviceAt(Instant.parse("2026-09-06T08:00:00Z"))
                .refreshMobileSession(expired.rawToken()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("UNAUTHENTICATED");

        var revoked = service.loginMobile("person@example.com", "correct-password");
        service.logout(revoked.rawToken());
        assertThatThrownBy(() -> service.refreshMobileSession(revoked.rawToken()))
                .isInstanceOf(EmailAuthException.class)
                .hasMessage("UNAUTHENTICATED");
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
