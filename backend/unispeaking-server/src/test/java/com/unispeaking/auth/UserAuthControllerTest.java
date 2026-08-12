package com.unispeaking.auth;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.GlobalExceptionHandler;
import com.unispeaking.infrastructure.email.VerificationEmailSender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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
                Duration.ofMinutes(10));
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
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-old-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/email/password/login")
                        .contentType("application/json")
                        .content("{\"email\":\"person@example.com\",\"password\":\"correct-new-password\"}"))
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
