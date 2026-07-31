package com.unispeaking.common.response;

public record ApiResponse<T>(
		boolean success,
		String code,
		String message,
		T data) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, "OK", "success", data);
	}

	public static <T> ApiResponse<T> failure(String code, String message) {
		return new ApiResponse<>(false, code, message, null);
	}
}
