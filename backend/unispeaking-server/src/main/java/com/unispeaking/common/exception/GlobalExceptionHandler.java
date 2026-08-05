package com.unispeaking.common.exception;

import com.unispeaking.common.exception.interview.InterviewException;
import com.unispeaking.common.response.ApiResponse;
import com.unispeaking.domain.dto.scene.InterviewApiRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
		HttpStatus status = exception instanceof InterviewException interviewException
				? interviewException.errorCode().status()
				: switch (exception.code()) {
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
	public ResponseEntity<ApiResponse<Void>> handleValidationException(
			MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.orElse("Invalid request");
		boolean interviewRequest = exception.getBindingResult().getTarget()
				instanceof InterviewApiRequest;
		String code = interviewRequest ? "INTERVIEW_INPUT_INVALID" : "VALIDATION_ERROR";
		HttpStatus status = interviewRequest
				? HttpStatus.UNPROCESSABLE_ENTITY
				: HttpStatus.BAD_REQUEST;
		return ResponseEntity.status(status).body(ApiResponse.failure(code, message));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(
			MaxUploadSizeExceededException exception,
			HttpServletRequest request) {
		boolean interviewRequest = isInterviewRequest(request);
		String code = interviewRequest
				? "INTERVIEW_PAYLOAD_TOO_LARGE"
				: "PAYLOAD_TOO_LARGE";
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(ApiResponse.failure(code, "Request payload is too large"));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
			HttpMediaTypeNotSupportedException exception,
			HttpServletRequest request) {
		boolean interviewRequest = isInterviewRequest(request);
		String code = interviewRequest
				? "INTERVIEW_MEDIA_TYPE_UNSUPPORTED"
				: "MEDIA_TYPE_UNSUPPORTED";
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(ApiResponse.failure(code, "Request media type is not supported"));
	}

	private static boolean isInterviewRequest(HttpServletRequest request) {
		if (request == null) {
			return false;
		}
		String uri = request.getRequestURI();
		return uri.equals("/api/interviews") || uri.startsWith("/api/interviews/");
	}
}
