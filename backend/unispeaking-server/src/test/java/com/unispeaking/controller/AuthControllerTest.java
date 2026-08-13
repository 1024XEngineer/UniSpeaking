package com.unispeaking.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.auth.EmailAuthService;
import com.unispeaking.common.exception.GlobalExceptionHandler;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

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
                .thenReturn(new EmailAuthService.UserView(
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
                .thenReturn(new EmailAuthService.UserView(
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
                .thenReturn(new EmailAuthService.UserView(
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
