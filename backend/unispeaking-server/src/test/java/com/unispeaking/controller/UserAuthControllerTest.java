package com.unispeaking.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.unispeaking.common.email.VerificationEmailSender;
import com.unispeaking.common.exception.GlobalExceptionHandler;
import com.unispeaking.infrastructure.persistence.repository.auth.InMemoryEmailAuthStore;
import com.unispeaking.service.auth.EmailAuthService;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.auth.RefreshTokenService;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.EmailAuthUser;
import com.unispeaking.domain.dto.auth.EmailLoginResult;
import com.unispeaking.domain.dto.auth.UserAccountResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAuthControllerTest {

    private CapturingEmailSender emailSender;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        emailSender = new CapturingEmailSender();
        var service = new EmailAuthService(
                emailSender,
                token -> "local-human-verified".equals(token),
                Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(),
                Clock.fixed(Instant.parse("2026-08-06T08:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(10),
                new InMemoryEmailAuthStore());
        mvc = MockMvcBuilders.standaloneSetup(new UserAuthController(service, false, 3600))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registersLogsInReadsCurrentUserAndLogsOut() throws Exception {
        var challenge = mvc.perform(post("/api/auth/email/challenges")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\","
                                + "\"humanVerificationToken\":\"local-human-verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresInSeconds", equalTo(600)))
                .andReturn();
        var challengeId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(challenge.getResponse().getContentAsString(), "$.data.challengeId"));

        var register = mvc.perform(post("/api/auth/email/register")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-password\","
                                + "\"challengeId\":\"" + challengeId + "\",\"code\":\"" + emailSender.code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("us-user-session"))
                .andExpect(jsonPath("$.data.email", equalTo("person@example.com")))
                .andReturn();

        var cookie = register.getResponse().getCookie("us-user-session");
        mvc.perform(get("/api/auth/email/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email", equalTo("person@example.com")));

        mvc.perform(post("/api/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/email/me").cookie(cookie))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/auth/email/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void issuesResetChallengeAndReplacesThePassword() throws Exception {
        var registrationChallenge = issueChallenge("/api/auth/email/challenges");
        mvc.perform(post("/api/auth/email/register")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-old-password\","
                                + "\"challengeId\":\"" + registrationChallenge + "\",\"code\":\""
                                + emailSender.code + "\"}"))
                .andExpect(status().isOk());

        var resetChallenge = issueChallenge("/api/auth/email/password-reset/challenges");
        mvc.perform(post("/api/auth/email/password-reset")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-new-password\","
                                + "\"challengeId\":\"" + resetChallenge + "\",\"code\":\""
                                + emailSender.code + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/email/password/login")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-old-password\","
                                + "\"humanVerificationToken\":\"local-human-verified\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/email/password/login")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-new-password\","
                                + "\"humanVerificationToken\":\"local-human-verified\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("us-user-session"));
    }

    @Test
    void rejectsWeakResetPasswordBeforeConsumingTheChallenge() throws Exception {
        mvc.perform(post("/api/auth/email/password-reset")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"short\","
                                + "\"challengeId\":\"00000000-0000-0000-0000-000000000001\","
                                + "\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    void rejectsPasswordLoginWhenHumanVerificationFails() throws Exception {
        var registrationChallenge = issueChallenge("/api/auth/email/challenges");
        mvc.perform(post("/api/auth/email/register")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-password\","
                                + "\"challengeId\":\"" + registrationChallenge + "\",\"code\":\""
                                + emailSender.code + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/email/password/login")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-password\","
                                + "\"humanVerificationToken\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("HUMAN_VERIFICATION_REQUIRED")))
                .andExpect(cookie().doesNotExist("us-user-session"));
    }

    @Test
    void rejectsPasswordLoginWhenHumanVerificationTokenIsMissing() throws Exception {
        mvc.perform(post("/api/auth/email/password/login")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(cookie().doesNotExist("us-user-session"));
    }

    @Test
    void logoutWithoutCookieStillExpiresTheSessionCookie() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("us-user-session=;")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    void tokenEndpointsLoginAndRegisterIssueLearningRefreshCookie() throws Exception {
        EmailAuthService email = mock(EmailAuthService.class);
        AuthService learning = mock(AuthService.class);
        RefreshTokenService refresh = mock(RefreshTokenService.class);
        UUID userId = UUID.randomUUID();
        var account = new UserAccountResponse(
                userId, "person@example.com", "Person", "LEARNER", "ACTIVE", null,
                Instant.parse("2026-08-06T08:00:00Z"));
        var auth = new AuthResponse("Bearer", "access-token",
                Instant.parse("2026-08-06T09:00:00Z"), account);
        when(email.login("person@example.com", "correct-password", "human"))
                .thenReturn(new EmailLoginResult("email-session", new EmailAuthUser(userId, "person@example.com")));
        when(learning.login(new com.unispeaking.domain.dto.auth.LoginRequest(
                "person@example.com", "correct-password"))).thenReturn(auth);
        when(refresh.issue(userId)).thenReturn(new RefreshTokenService.Issued(
                "refresh-token", Instant.parse("2026-08-07T08:00:00Z")));
        MockMvc configured = MockMvcBuilders.standaloneSetup(
                new UserAuthController(email, learning, true, 3600, refresh)).build();

        configured.perform(post("/api/auth/email/password/login/token")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-password\",\"humanVerificationToken\":\"human\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Set-Cookie", org.hamcrest.Matchers.containsString("us-learning-refresh=refresh-token")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Secure")));

        when(email.register("person@example.com", "correct-password", userId, "123456", "Person"))
                .thenReturn(new EmailAuthUser(userId, "person@example.com"));
        when(learning.login(new com.unispeaking.domain.dto.auth.LoginRequest(
                "person@example.com", "correct-password"))).thenReturn(auth);
        configured.perform(post("/api/auth/email/register/token")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-password\",\"challengeId\":\""
                                + userId + "\",\"code\":\"123456\",\"nickname\":\"Person\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));

        when(learning.login(new com.unispeaking.domain.dto.auth.LoginRequest(
                "person@example.com", "correct-password"))).thenReturn(null);
        configured.perform(post("/api/auth/email/password/login/token")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-password\",\"humanVerificationToken\":\"human\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));

        verify(learning, org.mockito.Mockito.times(3)).login(
                new com.unispeaking.domain.dto.auth.LoginRequest("person@example.com", "correct-password"));
    }

    @Test
    void tokenEndpointReportsMissingLearningAuthConfiguration() throws Exception {
        EmailAuthService email = mock(EmailAuthService.class);
        when(email.login("person@example.com", "correct-password", "local-human-verified"))
                .thenReturn(new EmailLoginResult("email-session",
                        new EmailAuthUser(UUID.randomUUID(), "person@example.com")));
        var controller = new UserAuthController(email, false, 3600);
        assertThrows(IllegalStateException.class, () -> controller.loginToken(
                new UserAuthController.LoginRequest(
                        "person@example.com", "correct-password", "local-human-verified"),
                mock(jakarta.servlet.http.HttpServletResponse.class)));
        assertThrows(IllegalStateException.class, () -> controller.registerToken(
                new UserAuthController.RegisterRequest(
                        "person@example.com", "correct-password", UUID.randomUUID(), "123456", null),
                mock(jakarta.servlet.http.HttpServletResponse.class)));
    }

    private UUID issueChallenge(String path) throws Exception {
        var challenge = mvc.perform(post(path)
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\","
                                + "\"humanVerificationToken\":\"local-human-verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresInSeconds", equalTo(600)))
                .andReturn();
        return UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(challenge.getResponse().getContentAsString(), "$.data.challengeId"));
    }

    private static final class CapturingEmailSender implements VerificationEmailSender {
        private String code;

        @Override
        public void sendVerificationCode(String recipient, String code, int ttlSeconds) {
            this.code = code;
        }
    }
}
