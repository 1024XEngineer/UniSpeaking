package com.unispeaking.common.exception;

import com.unispeaking.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
		HttpStatus status = switch (exception.code()) {
			case "AUTHENTICATION_REQUIRED", "INVALID_ACCESS_TOKEN", "ACCESS_TOKEN_REVOKED",
					"INVALID_CREDENTIALS", "USER_NOT_ACTIVE" -> HttpStatus.UNAUTHORIZED;
			case "SESSION_ACCESS_DENIED", "ADMIN_ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
			case "ACHIEVEMENT_UNLOCK_NOT_FOUND", "FEEDBACK_NOT_FOUND",
					"FEEDBACK_LOOKUP_DENIED" -> HttpStatus.NOT_FOUND;
			case "USERNAME_ALREADY_EXISTS", "PASSWORD_UPDATE_CONFLICT",
					"PROFILE_UPDATE_CONFLICT", "FEEDBACK_UPDATE_CONFLICT" -> HttpStatus.CONFLICT;
			case "OBJECT_STORAGE_FAILED" -> HttpStatus.BAD_GATEWAY;
			case "OBJECT_STORAGE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
			case "OCR_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
			case "OCR_PROCESS_FAILED", "OCR_RESPONSE_INVALID" -> HttpStatus.BAD_GATEWAY;
			case "OCR_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
			case "AVATAR_STORAGE_FAILED" -> HttpStatus.BAD_GATEWAY;
			case "AVATAR_STORAGE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
			case "ACHIEVEMENT_PERSISTENCE_FAILED", "FEEDBACK_PERSISTENCE_FAILED",
					"PROFILE_GOALS_PERSISTENCE_FAILED" ->
					HttpStatus.INTERNAL_SERVER_ERROR;
			case "AVATAR_DIMENSION_INVALID", "AVATAR_CONTENT_INVALID" ->
					HttpStatus.UNPROCESSABLE_ENTITY;
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
}
