package com.unispeaking.service.auth;

import com.unispeaking.domain.dto.account.ReactivateAccountRequest;
import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import com.unispeaking.domain.dto.auth.UserAccountResponse;

public interface AuthService {
	AuthResponse register(RegisterRequest request);
	AuthResponse login(LoginRequest request);
	default AuthResponse reactivate(ReactivateAccountRequest request) {
		throw new UnsupportedOperationException("Account reactivation is not supported");
	}
	UserAccountResponse currentUser();
	String requireUserId(String requestedUserId);
}
