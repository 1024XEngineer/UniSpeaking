package com.unispeaking.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.EmailAuthChallenge;
import com.unispeaking.service.auth.EmailAuthService;
import com.unispeaking.service.auth.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MobileEmailAuthControllerTest {

    @Test
    void issuesEmailChallengeWithoutHumanVerificationForMobile() {
        var emailAuthService = mock(EmailAuthService.class);
        var authService = mock(AuthService.class);
        var controller = new MobileEmailAuthController(emailAuthService, authService);
        when(emailAuthService.issueMobileChallenge("person@example.com"))
                .thenReturn(new EmailAuthChallenge(UUID.randomUUID(), 600, 60));

        controller.issueChallenge(new MobileEmailAuthController.EmailRequest("person@example.com"));

        verify(emailAuthService).issueMobileChallenge("person@example.com");
    }

    @Test
    void registersTheVerifiedEmailWithNicknameAndReturnsBusinessJwt() {
        var emailAuthService = mock(EmailAuthService.class);
        var authService = mock(AuthService.class);
        var controller = new MobileEmailAuthController(emailAuthService, authService);
        var challengeId = UUID.randomUUID();
        var request = new MobileEmailAuthController.RegisterRequest(
                "person@example.com", "correct-password", challengeId, "123456", "Sunny");

        controller.register(request);

        verify(emailAuthService).register(
                "person@example.com", "correct-password", challengeId, "123456", "Sunny");
        verify(authService).login(new LoginRequest("person@example.com", "correct-password"));
    }

    @Test
    void returnsBusinessJwtForLoginWithoutHumanVerification() {
        var emailAuthService = mock(EmailAuthService.class);
        var authService = mock(AuthService.class);
        var controller = new MobileEmailAuthController(emailAuthService, authService);
        var request = new MobileEmailAuthController.MobileLoginRequest(
                "person@example.com", "correct-password");

        controller.login(request);

        verify(authService).login(new LoginRequest("person@example.com", "correct-password"));
    }

    @Test
    void resetsPasswordThroughTheMobileEmailFlow() {
        var emailAuthService = mock(EmailAuthService.class);
        var authService = mock(AuthService.class);
        var controller = new MobileEmailAuthController(emailAuthService, authService);
        var challengeId = UUID.randomUUID();
        when(emailAuthService.issueMobileChallenge("person@example.com"))
                .thenReturn(new EmailAuthChallenge(challengeId, 600, 60));

        controller.issuePasswordResetChallenge(
                new MobileEmailAuthController.EmailRequest("person@example.com"));
        controller.resetPassword(new MobileEmailAuthController.ResetPasswordRequest(
                "person@example.com", "new-correct-password", challengeId, "123456"));

        verify(emailAuthService).issueMobileChallenge("person@example.com");
        verify(emailAuthService).resetPassword(
                "person@example.com", "new-correct-password", challengeId, "123456");
    }
}
