package com.unispeaking.auth;

import com.unispeaking.common.response.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public final class UserAuthController {

    public static final String COOKIE_NAME = "us-user-session";

    public record EmailRequest(
            @NotBlank @Email String email,
            @NotBlank String humanVerificationToken) {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 12, max = 200) String password,
            @NotNull UUID challengeId,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 12, max = 200) String password,
            @NotNull UUID challengeId,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
    }

    public record ChallengeResponse(UUID challengeId, int expiresInSeconds, int resendAfterSeconds) {
    }

    private final EmailAuthService authService;
    private final boolean secureCookie;
    private final long sessionMaxAgeSeconds;

    public UserAuthController(
            EmailAuthService authService,
            @Value("${AUTH_COOKIE_SECURE:false}") boolean secureCookie,
            @Value("${AUTH_SESSION_MAX_AGE_SECONDS:28800}") long sessionMaxAgeSeconds) {
        this.authService = authService;
        this.secureCookie = secureCookie;
        this.sessionMaxAgeSeconds = sessionMaxAgeSeconds;
    }

    @PostMapping("/email/challenges")
    public ApiResponse<ChallengeResponse> issueChallenge(@Valid @RequestBody EmailRequest request) {
        var challenge = authService.issueChallenge(request.email(), request.humanVerificationToken());
        return ApiResponse.success(new ChallengeResponse(
                challenge.challengeId(), challenge.expiresInSeconds(), challenge.resendAfterSeconds()));
    }

    @PostMapping("/email/password-reset/challenges")
    public ApiResponse<ChallengeResponse> issuePasswordResetChallenge(@Valid @RequestBody EmailRequest request) {
        var challenge = authService.issueChallenge(request.email(), request.humanVerificationToken());
        return ApiResponse.success(new ChallengeResponse(
                challenge.challengeId(), challenge.expiresInSeconds(), challenge.resendAfterSeconds()));
    }

    @PostMapping("/email/password-reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.email(), request.password(), request.challengeId(), request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/register")
    public ResponseEntity<ApiResponse<EmailAuthService.UserView>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        var user = authService.register(
                request.email(), request.password(), request.challengeId(), request.code());
        var login = authService.login(request.email(), request.password());
        addSessionCookie(response, login.rawToken());
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PostMapping("/email/password/login")
    public ResponseEntity<ApiResponse<EmailAuthService.UserView>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        var login = authService.login(request.email(), request.password());
        addSessionCookie(response, login.rawToken());
        return ResponseEntity.ok(ApiResponse.success(login.user()));
    }

    @GetMapping("/email/me")
    public ApiResponse<EmailAuthService.UserView> me(HttpServletRequest request) {
        return ApiResponse.success(authService.currentUser(readSessionCookie(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        var token = readOptionalSessionCookie(request);
        if (token != null) {
            authService.logout(token);
        }
        var expired = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        return ResponseEntity.noContent().build();
    }

    private void addSessionCookie(HttpServletResponse response, String token) {
        var cookie = ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(sessionMaxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String readSessionCookie(HttpServletRequest request) {
        var token = readOptionalSessionCookie(request);
        if (token == null) {
            throw new EmailAuthService.AuthException("UNAUTHENTICATED");
        }
        return token;
    }

    private static String readOptionalSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
