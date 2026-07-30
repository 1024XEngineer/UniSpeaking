package com.unispeaking.controller;

import com.unispeaking.domain.dto.account.ChangePasswordRequest;
import com.unispeaking.domain.dto.account.ReactivateAccountRequest;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import com.unispeaking.domain.dto.auth.UserAccountResponse;
import com.unispeaking.domain.dto.response.ApiResponse;
import com.unispeaking.service.account.AccountService;
import com.unispeaking.service.auth.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final AccountService accountService;

	public AuthController(AuthService authService, AccountService accountService) {
		this.authService = authService;
		this.accountService = accountService;
	}

	@PostMapping("/register")
	public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ApiResponse.success(authService.register(request));
	}

	@PostMapping("/login")
	public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.success(authService.login(request));
	}

	@PostMapping("/reactivate")
	public ApiResponse<AuthResponse> reactivate(
			@Valid @RequestBody ReactivateAccountRequest request) {
		return ApiResponse.success(authService.reactivate(request));
	}

	@PutMapping("/password")
	public ApiResponse<Void> changePassword(
			@Valid @RequestBody ChangePasswordRequest request) {
		accountService.changePassword(
				UUID.fromString(authService.requireUserId(null)),
				request.currentPassword(),
				request.newPassword());
		return ApiResponse.success(null);
	}

	@GetMapping("/me")
	public ApiResponse<UserAccountResponse> me() {
		return ApiResponse.success(authService.currentUser());
	}
}
