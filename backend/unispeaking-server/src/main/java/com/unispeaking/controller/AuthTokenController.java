package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.MobileAuthResponse;
import com.unispeaking.domain.dto.auth.RefreshTokenRequest;
import com.unispeaking.service.auth.RefreshTokenService;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import com.unispeaking.infrastructure.config.WebOriginProperties;
import org.springframework.util.PatternMatchUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthTokenController {
    public static final String COOKIE_NAME = "us-learning-refresh";
    private final RefreshTokenService service;
    private final boolean secureCookie;
    private final WebOriginProperties webOriginProperties;

    public AuthTokenController(RefreshTokenService service,
                               @Value("${AUTH_COOKIE_SECURE:false}") boolean secureCookie,
                               WebOriginProperties webOriginProperties) {
        this.service = service;
        this.secureCookie = secureCookie;
        this.webOriginProperties = webOriginProperties;
    }

    @PostMapping("/web/token/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshWeb(HttpServletRequest request, HttpServletResponse response) {
        requireTrustedOrigin(request);
        String raw = cookie(request);
        var result = service.refresh(raw);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), result.refreshTokenExpiresAt()).toString());
        return ResponseEntity.ok(ApiResponse.success(result.access()));
    }

    @PostMapping("/mobile/token/refresh")
    public ApiResponse<MobileAuthResponse> refreshMobile(@Valid @RequestBody RefreshTokenRequest request) {
        var result = service.refresh(request.refreshToken());
        var access = result.access();
        return ApiResponse.success(new MobileAuthResponse(access.tokenType(), access.accessToken(), access.expiresAt(),
                result.refreshToken(), result.refreshTokenExpiresAt(), access.user()));
    }

    @PostMapping("/web/token/revoke")
    public ResponseEntity<Void> revokeWeb(HttpServletRequest request, HttpServletResponse response) {
        requireTrustedOrigin(request);
        String raw = optionalCookie(request);
        service.revoke(raw);
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie().toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mobile/token/revoke")
    public ResponseEntity<Void> revokeMobile(@Valid @RequestBody RefreshTokenRequest request) {
        service.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie cookie(String token, Instant expiresAt) {
        return ResponseCookie.from(COOKIE_NAME, token).httpOnly(true).secure(secureCookie)
                .sameSite("Lax").path("/api/auth/web/token").build();
    }

    private ResponseCookie expiredCookie() {
        return ResponseCookie.from(COOKIE_NAME, "").httpOnly(true).secure(secureCookie)
                .sameSite("Lax").path("/api/auth/web/token").maxAge(Duration.ZERO).build();
    }

    private static String cookie(HttpServletRequest request) {
        String raw = optionalCookie(request);
        if (raw == null || raw.isBlank()) throw new com.unispeaking.common.exception.BusinessException("REFRESH_TOKEN_INVALID", "登录状态已失效，请重新登录");
        return raw;
    }

    private static String optionalCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private void requireTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            origin = request.getHeader("Referer");
        }
        final String requestOrigin = origin;
        if (requestOrigin == null || requestOrigin.isBlank()
                || webOriginProperties.getAllowedOriginPatterns().stream()
                        .noneMatch(pattern -> PatternMatchUtils.simpleMatch(pattern, requestOrigin))) {
            throw new com.unispeaking.common.exception.BusinessException("AUTH_ORIGIN_INVALID", "请求来源无效");
        }
    }
}
