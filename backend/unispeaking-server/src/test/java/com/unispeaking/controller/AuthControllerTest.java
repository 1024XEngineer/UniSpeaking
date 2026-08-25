package com.unispeaking.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.service.auth.EmailAuthService;
import com.unispeaking.domain.dto.auth.EmailAuthUser;
import com.unispeaking.common.exception.GlobalExceptionHandler;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.UserAccountResponse;
import com.unispeaking.domain.dto.auth.ChangePasswordRequest;
import com.unispeaking.domain.dto.auth.ChangePasswordResponse;
import com.unispeaking.service.auth.RefreshTokenService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {
	@Test
	void issuesSecureRefreshCookieAndCoversDirectAccountEndpointsAndCookieGuards() {
		AuthService authService = mock(AuthService.class);
		EmailAuthService emailAuthService = mock(EmailAuthService.class);
		RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
		UUID userId = UUID.randomUUID();
		UserAccountResponse user = new UserAccountResponse(userId, "person@example.com", "Person", "USER", "ACTIVE", null, Instant.EPOCH);
		AuthResponse auth = new AuthResponse("Bearer", "access", Instant.now(), user);
		when(emailAuthService.currentUser("verified")).thenReturn(new EmailAuthUser(userId, "person@example.com"));
		when(authService.login(new LoginRequest("person@example.com", "password"))).thenReturn(auth);
		when(refreshTokens.issue(userId)).thenReturn(new RefreshTokenService.Issued("refresh", Instant.now()));
		when(authService.currentUser()).thenReturn(user);
		when(authService.changePassword(new ChangePasswordRequest("current", "updated"))).thenReturn(ChangePasswordResponse.required());
		AuthController controller = new AuthController(authService, emailAuthService, refreshTokens, true);
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new jakarta.servlet.http.Cookie("other", "value"), new jakarta.servlet.http.Cookie(UserAuthController.COOKIE_NAME, "verified"));
		MockHttpServletResponse response = new MockHttpServletResponse();
		controller.login(new LoginRequest("person@example.com", "password"), request, response);
		org.junit.jupiter.api.Assertions.assertTrue(response.getHeader("Set-Cookie").contains("Secure"));
		org.junit.jupiter.api.Assertions.assertEquals(user, controller.me().data());
		org.junit.jupiter.api.Assertions.assertTrue(controller.changePassword(new ChangePasswordRequest("current", "updated")).data().reauthenticationRequired());

		MockHttpServletRequest blankCookie = new MockHttpServletRequest();
		blankCookie.setCookies(new jakarta.servlet.http.Cookie(UserAuthController.COOKIE_NAME, " "));
		assertThrows(com.unispeaking.common.exception.EmailAuthException.class,
				() -> controller.login(new LoginRequest("person@example.com", "password"), blankCookie, new MockHttpServletResponse()));

		when(authService.login(new LoginRequest("person@example.com", "password"))).thenReturn(null);
		controller.login(new LoginRequest("person@example.com", "password"), request, new MockHttpServletResponse());
		when(authService.login(new LoginRequest("person@example.com", "password"))).thenReturn(new AuthResponse("Bearer", "access", Instant.now(), null));
		controller.login(new LoginRequest("person@example.com", "password"), request, new MockHttpServletResponse());
		verify(refreshTokens).issue(userId);
	}

    @Test
    void rejectsBusinessJwtLoginWithoutVerifiedEmailSession() throws Exception {
        var authService = mock(AuthService.class);
        var emailAuthService = mock(EmailAuthService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, emailAuthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"person@example.com\",\"password\":\"correct-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("HUMAN_VERIFICATION_REQUIRED")));

        verifyNoInteractions(authService, emailAuthService);
    }

    @Test
    void rejectsBusinessJwtLoginWhenVerifiedEmailDoesNotMatch() throws Exception {
        var authService = mock(AuthService.class);
        var emailAuthService = mock(EmailAuthService.class);
        when(emailAuthService.currentUser("verified-session"))
                .thenReturn(new EmailAuthUser(
                        java.util.UUID.randomUUID(), "other@example.com"));
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, emailAuthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/auth/login")
                        .cookie(new jakarta.servlet.http.Cookie("us-user-session", "verified-session"))
                        .contentType("application/json")
                        .content("{\"username\":\"person@example.com\",\"password\":\"correct-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("HUMAN_VERIFICATION_REQUIRED")));

        verifyNoInteractions(authService);
    }

    @Test
    void allowsBusinessJwtLoginForTheVerifiedEmailSession() throws Exception {
        var authService = mock(AuthService.class);
        var emailAuthService = mock(EmailAuthService.class);
        when(emailAuthService.currentUser("verified-session"))
                .thenReturn(new EmailAuthUser(
                        java.util.UUID.randomUUID(), "person@example.com"));
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, emailAuthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/auth/login")
                        .cookie(new jakarta.servlet.http.Cookie("us-user-session", "verified-session"))
                        .contentType("application/json")
                        .content("{\"username\":\"Person@Example.com\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk());

        verify(authService).login(new LoginRequest("Person@Example.com", "correct-password"));
    }

    @Test
    void rejectsBusinessRegistrationWithoutVerifiedEmailSession() throws Exception {
        var authService = mock(AuthService.class);
        var emailAuthService = mock(EmailAuthService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, emailAuthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"person@example.com\",\"password\":\"correct-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("HUMAN_VERIFICATION_REQUIRED")));

        verifyNoInteractions(authService, emailAuthService);
    }

    @Test
    void allowsBusinessRegistrationForTheVerifiedEmailSession() throws Exception {
        var authService = mock(AuthService.class);
        var emailAuthService = mock(EmailAuthService.class);
        when(emailAuthService.currentUser("verified-session"))
                .thenReturn(new EmailAuthUser(
                        java.util.UUID.randomUUID(), "person@example.com"));
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, emailAuthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/auth/register")
                        .cookie(new jakarta.servlet.http.Cookie("us-user-session", "verified-session"))
                        .contentType("application/json")
                        .content("{\"username\":\"person@example.com\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk());

        verify(authService).register(new RegisterRequest("person@example.com", "correct-password", null));
    }
}
