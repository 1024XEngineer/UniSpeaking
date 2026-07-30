package com.unispeaking.exception;

import com.unispeaking.domain.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
		HttpStatus status = switch (exception.code()) {
			case "AUTHENTICATION_REQUIRED", "INVALID_ACCESS_TOKEN", "ACCESS_TOKEN_REVOKED",
					"INVALID_CREDENTIALS", "USER_NOT_ACTIVE" -> HttpStatus.UNAUTHORIZED;
			case "SESSION_ACCESS_DENIED", "ACCOUNT_PENDING_DELETION" -> HttpStatus.FORBIDDEN;
			case "USERNAME_ALREADY_EXISTS" -> HttpStatus.CONFLICT;
			case "USER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
			case "AVATAR_TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE;
			case "PROFILE_OVERVIEW_UNAVAILABLE", "AVATAR_STORAGE_UNAVAILABLE",
					"AVATAR_STORAGE_FAILED" -> HttpStatus.SERVICE_UNAVAILABLE;
			case "CURRENT_PASSWORD_INVALID", "NEW_PASSWORD_SAME_AS_CURRENT",
					"ACCOUNT_REACTIVATION_NOT_ALLOWED" -> HttpStatus.BAD_REQUEST;
			default -> HttpStatus.BAD_REQUEST;
		};
		return ResponseEntity.status(status)
				.body(ApiResponse.failure(exception.code(), exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.orElse("Invalid request");
		return ApiResponse.failure("VALIDATION_ERROR", message);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	@ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
	public ApiResponse<Void> handleAvatarTooLarge(MaxUploadSizeExceededException exception) {
		return ApiResponse.failure("AVATAR_TOO_LARGE", "头像不能超过 2 MiB");
	}
}
