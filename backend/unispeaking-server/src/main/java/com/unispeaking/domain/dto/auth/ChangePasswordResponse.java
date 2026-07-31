package com.unispeaking.domain.dto.auth;

public record ChangePasswordResponse(boolean reauthenticationRequired) {
	public static ChangePasswordResponse required() {
		return new ChangePasswordResponse(true);
	}
}
