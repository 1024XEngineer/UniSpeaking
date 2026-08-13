package com.unispeaking.controller;

import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.auth.EmailAuthChallenge;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.auth.EmailAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
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
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        emailAuthService.register(
                request.email(), request.password(), request.challengeId(), request.code(), request.nickname());
        return ApiResponse.success(authService.login(
                new LoginRequest(request.email(), request.password())));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody MobileLoginRequest request) {
        return ApiResponse.success(authService.login(
                new LoginRequest(request.email(), request.password())));
    }
}
