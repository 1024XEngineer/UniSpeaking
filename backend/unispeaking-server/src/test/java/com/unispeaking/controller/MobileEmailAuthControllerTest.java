package com.unispeaking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.EmailAuthChallenge;
import com.unispeaking.domain.dto.auth.EmailAuthUser;
import com.unispeaking.domain.dto.auth.EmailLoginResult;
import com.unispeaking.domain.dto.auth.UserAccountResponse;
import com.unispeaking.service.auth.EmailAuthService;
import com.unispeaking.service.auth.AuthService;
import java.time.Instant;
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
        when(authService.login(new LoginRequest("person@example.com", "correct-password")))
                .thenReturn(access());
        when(emailAuthService.loginMobile("person@example.com", "correct-password"))
                .thenReturn(mobileSession());

        var response = controller.register(request).data();

        assertEquals("refresh-token", response.refreshToken());
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
        when(authService.login(new LoginRequest("person@example.com", "correct-password")))
                .thenReturn(access());
        when(emailAuthService.loginMobile("person@example.com", "correct-password"))
                .thenReturn(mobileSession());

        var response = controller.login(request).data();

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        verify(authService).login(new LoginRequest("person@example.com", "correct-password"));
    }

    @Test
    void refreshesAccessTokenFromThePersistentMobileSession() {
        var emailAuthService = mock(EmailAuthService.class);
        var authService = mock(AuthService.class);
        var controller = new MobileEmailAuthController(emailAuthService, authService);
        when(emailAuthService.refreshMobileSession("refresh-token"))
                .thenReturn(new EmailAuthUser(userId(), "person@example.com"));
        when(authService.issueAccessToken(userId().toString())).thenReturn(access());

        var response = controller.refresh(
                new MobileEmailAuthController.MobileSessionRequest("refresh-token")).data();

        assertEquals("access-token", response.accessToken());
        verify(emailAuthService).refreshMobileSession("refresh-token");
        verify(authService).issueAccessToken(userId().toString());
    }

    @Test
    void revokesThePersistentMobileSessionOnLogout() {
        var emailAuthService = mock(EmailAuthService.class);
        var controller = new MobileEmailAuthController(emailAuthService, mock(AuthService.class));

        var response = controller.logout(
                new MobileEmailAuthController.MobileSessionRequest("refresh-token"));

        assertEquals(204, response.getStatusCode().value());
        verify(emailAuthService).logout("refresh-token");
    }

    private static AuthResponse access() {
        var now = Instant.parse("2026-08-19T08:00:00Z");
        return new AuthResponse(
                "Bearer",
                "access-token",
                now.plusSeconds(7200),
                new UserAccountResponse(userId(), "person@example.com", "Sunny", "USER", "ACTIVE", now, now));
    }

    private static EmailLoginResult mobileSession() {
        return new EmailLoginResult(
                "refresh-token",
                new EmailAuthUser(userId(), "person@example.com"));
    }

    private static UUID userId() {
        return UUID.fromString("22222222-2222-4222-8222-222222222222");
    }
}
