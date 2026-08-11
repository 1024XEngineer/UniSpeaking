package com.unispeaking.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unispeaking.infrastructure.email.VerificationEmailSender;
import com.unispeaking.auth.HumanVerificationGateway;
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
    private EmailAuthService service;

    @BeforeEach
    void setUp() {
        emailSender = new CapturingEmailSender();
        service = new EmailAuthService(
                emailSender,
                token -> "verified-human".equals(token),
                Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                Clock.fixed(Instant.parse("2026-08-06T08:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(10));
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
                .isInstanceOf(EmailAuthService.AuthException.class)
                .hasMessage("CHALLENGE_INVALID");
    }

    @Test
    void incorrectPasswordDoesNotCreateSession() {
        var challenge = service.issueChallenge("person@example.com", "verified-human");
        service.register("person@example.com", "correct-password", challenge.challengeId(), emailSender.lastCode());

        assertThatThrownBy(() -> service.login("person@example.com", "wrong-password"))
                .isInstanceOf(EmailAuthService.AuthException.class)
                .hasMessage("INVALID_CREDENTIALS");
    }

    @Test
    void rejectsChallengeBeforeEmailDeliveryWhenHumanVerificationFails() {
        assertThatThrownBy(() -> service.issueChallenge("person@example.com", "invalid"))
                .isInstanceOf(EmailAuthService.AuthException.class)
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
                .isInstanceOf(EmailAuthService.AuthException.class)
                .hasMessage("UNAUTHENTICATED");
        assertThatThrownBy(() -> service.login("person@example.com", "correct-old-password"))
                .isInstanceOf(EmailAuthService.AuthException.class)
                .hasMessage("INVALID_CREDENTIALS");
        assertThat(service.login("person@example.com", "correct-new-password").user().email())
                .isEqualTo("person@example.com");
        assertThatThrownBy(() -> service.resetPassword(
                "person@example.com", "another-new-password", resetChallenge.challengeId(),
                emailSender.lastCode()))
                .isInstanceOf(EmailAuthService.AuthException.class)
                .hasMessage("CHALLENGE_INVALID");
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
