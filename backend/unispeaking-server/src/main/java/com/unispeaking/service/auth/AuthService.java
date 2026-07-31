package com.unispeaking.service.auth;

import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.ChangePasswordRequest;
import com.unispeaking.domain.dto.auth.ChangePasswordResponse;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import com.unispeaking.domain.dto.auth.UserAccountResponse;

public interface AuthService {
	AuthResponse register(RegisterRequest request);
	AuthResponse login(LoginRequest request);
	UserAccountResponse currentUser();
	ChangePasswordResponse changePassword(ChangePasswordRequest request);
	String requireUserId(String requestedUserId);
}
