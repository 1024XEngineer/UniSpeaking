package com.unispeaking.service.auth;

import com.unispeaking.domain.dto.auth.AuthResponse;
import com.unispeaking.domain.dto.auth.ChangePasswordRequest;
import com.unispeaking.domain.dto.auth.ChangePasswordResponse;
import com.unispeaking.domain.dto.auth.LoginRequest;
import com.unispeaking.domain.dto.auth.RegisterRequest;
import com.unispeaking.domain.dto.auth.UserAccountResponse;

public interface AuthService {
	/** Creates an account and returns its authenticated session. */
	AuthResponse register(RegisterRequest request);

	/** Authenticates an account with the supplied credentials. */
	AuthResponse login(LoginRequest request);

	/** Returns the currently authenticated account. */
	UserAccountResponse currentUser();

	/** Changes the current user's password when supported. */
	default ChangePasswordResponse changePassword(ChangePasswordRequest request) {
		throw new UnsupportedOperationException("Password change is not supported");
	}

	/** Returns the authenticated user ID, or {@code null} for an anonymous request. */
	default String currentUserIdOrNull() {
		return null;
	}

	/** Resolves and validates the user ID allowed for the current request. */
	String requireUserId(String requestedUserId);

	/** Returns the authenticated administrator ID when admin access is supported. */
	default String requireAdminUserId() {
		throw new UnsupportedOperationException("Admin access is not supported");
	}
}
