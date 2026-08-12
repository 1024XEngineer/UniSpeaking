package com.unispeaking.admin.auth.web;

import com.unispeaking.admin.auth.application.AuthService;
import com.unispeaking.admin.auth.security.SessionAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("adminAuthController")
@RequestMapping("/api/admin/auth")
public final class AuthController {
    // API requests may be served as /api/* locally or /backend/api/* in production.
    // Root scope keeps the HttpOnly session cookie available across both prefixes.
    private static final String COOKIE_PATH = "/";
    public record LoginRequest(
            @NotBlank String login,
            @NotBlank @Size(min = 12, max = 200) String password) {
    }

    public record AdminMe(String id, String login, String role) {
    }

    private final AuthService authService;
    private final boolean secureCookie;
    private final long absoluteSeconds;

    public AuthController(
            AuthService authService,
            @Value("${unispeaking.admin.secure-cookie:true}") boolean secureCookie,
            @Value("${unispeaking.admin.session-absolute-seconds:28800}") long absoluteSeconds) {
        this.authService = authService;
        this.secureCookie = secureCookie;
        this.absoluteSeconds = absoluteSeconds;
    }

    @PostMapping("/login")
    ResponseEntity<Void> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        var login = authService.login(request.login(), request.password());
        var cookie = ResponseCookie.from("us-admin-session", login.rawToken())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ofSeconds(absoluteSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    AdminMe me(org.springframework.security.core.Authentication authentication) {
        var administrator = (AuthService.CurrentAdmin) authentication.getPrincipal();
        return new AdminMe(
                administrator.id().toString(),
                administrator.login(),
                administrator.role());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        SessionAuthenticationFilter.findCookie(request).ifPresent(authService::logout);
        var expired = ResponseCookie.from(SessionAuthenticationFilter.COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        return ResponseEntity.noContent().build();
    }
}
