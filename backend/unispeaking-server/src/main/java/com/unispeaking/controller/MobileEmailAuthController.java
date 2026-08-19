package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.auth.EmailAuthChallenge;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.UserAccountResponse;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.auth.EmailAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JWT-returning email auth endpoints for native clients without browser cookie storage. */
@RestController
@RequestMapping("/api/auth/mobile/email")
public final class MobileEmailAuthController {

    public record EmailRequest(@NotBlank @Email String email) {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 12, max = 200) String password,
            @NotNull UUID challengeId,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
            @Size(max = 32) String nickname) {
    }

    public record MobileLoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record MobileSessionRequest(@NotBlank String refreshToken) {
    }

    public record MobileAuthResponse(
            String tokenType,
            String accessToken,
            Instant expiresAt,
            UserAccountResponse user,
            String refreshToken) {
    }

    public record ChallengeResponse(UUID challengeId, int expiresInSeconds, int resendAfterSeconds) {
    }

    private final EmailAuthService emailAuthService;
    private final AuthService authService;

    public MobileEmailAuthController(EmailAuthService emailAuthService, AuthService authService) {
        this.emailAuthService = emailAuthService;
        this.authService = authService;
    }

    @PostMapping("/challenges")
    public ApiResponse<ChallengeResponse> issueChallenge(@Valid @RequestBody EmailRequest request) {
        var challenge = emailAuthService.issueMobileChallenge(request.email());
        return ApiResponse.success(new ChallengeResponse(
                challenge.challengeId(), challenge.expiresInSeconds(), challenge.resendAfterSeconds()));
    }

    @PostMapping("/register")
    public ApiResponse<MobileAuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        emailAuthService.register(
                request.email(), request.password(), request.challengeId(), request.code(), request.nickname());
        var access = authService.login(new LoginRequest(request.email(), request.password()));
        var mobileSession = emailAuthService.loginMobile(request.email(), request.password());
        return ApiResponse.success(response(access, mobileSession.rawToken()));
    }

    @PostMapping("/login")
    public ApiResponse<MobileAuthResponse> login(@Valid @RequestBody MobileLoginRequest request) {
        var access = authService.login(new LoginRequest(request.email(), request.password()));
        var mobileSession = emailAuthService.loginMobile(request.email(), request.password());
        return ApiResponse.success(response(access, mobileSession.rawToken()));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody MobileSessionRequest request) {
        var user = emailAuthService.refreshMobileSession(request.refreshToken());
        return ApiResponse.success(authService.issueAccessToken(user.id().toString()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody MobileSessionRequest request) {
        emailAuthService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private MobileAuthResponse response(AuthResponse access, String refreshToken) {
        return new MobileAuthResponse(
                access.tokenType(),
                access.accessToken(),
                access.expiresAt(),
                access.user(),
                refreshToken);
    }
}
