package com.unispeaking.common.exception;

import com.unispeaking.common.response.ApiResponse;
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
		HttpStatus status = switch (exception.code()) {
			case "AUTHENTICATION_REQUIRED", "INVALID_ACCESS_TOKEN", "ACCESS_TOKEN_REVOKED",
					"INVALID_CREDENTIALS", "USER_NOT_ACTIVE" -> HttpStatus.UNAUTHORIZED;
			case "SESSION_ACCESS_DENIED", "ADMIN_ACCESS_DENIED",
					InterviewErrorCode.INTERVIEW_SCENE_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
			case "ACHIEVEMENT_UNLOCK_NOT_FOUND", "FEEDBACK_NOT_FOUND",
					"FEEDBACK_LOOKUP_DENIED", "IELTS_RECORDING_NOT_FOUND",
					InterviewErrorCode.INTERVIEW_SCENE_NOT_FOUND,
					InterviewErrorCode.INTERVIEW_REPORT_NOT_FOUND,
					InterviewErrorCode.INTERVIEW_RECORDING_NOT_FOUND ->
					HttpStatus.NOT_FOUND;
			case "USERNAME_ALREADY_EXISTS", "PASSWORD_UPDATE_CONFLICT",
					"PROFILE_UPDATE_CONFLICT", "FEEDBACK_UPDATE_CONFLICT",
					InterviewErrorCode.INTERVIEW_TURN_CONTENT_MISMATCH,
					InterviewErrorCode.INTERVIEW_TURN_MESSAGE_PENDING,
					InterviewErrorCode.INTERVIEW_SESSION_ENDED ->
					HttpStatus.CONFLICT;
			case InterviewErrorCode.INTERVIEW_DAILY_LIMIT_REACHED ->
					HttpStatus.TOO_MANY_REQUESTS;
			case "OBJECT_STORAGE_FAILED" -> HttpStatus.BAD_GATEWAY;
			case "OBJECT_STORAGE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
			case "OCR_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
			case "OCR_PROCESS_FAILED", "OCR_RESPONSE_INVALID" -> HttpStatus.BAD_GATEWAY;
			case "OCR_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
			case "OCR_INPUT_REQUIRED", "OCR_TOO_MANY_IMAGES",
					"OCR_TOTAL_SIZE_EXCEEDED", "OCR_PIXEL_LIMIT_EXCEEDED",
					"DOCUMENT_INPUT_REQUIRED", "DOCUMENT_TOO_LARGE",
					"DOCUMENT_PDF_PAGE_LIMIT_EXCEEDED",
					"DOCUMENT_TEXT_EMPTY", "DOCUMENT_TEXT_TOO_LARGE" ->
					HttpStatus.BAD_REQUEST;
			case "OCR_FORMAT_UNSUPPORTED", "OCR_CONTENT_INVALID",
					"DOCUMENT_FORMAT_UNSUPPORTED", "DOCUMENT_CONTENT_INVALID" ->
					HttpStatus.UNPROCESSABLE_ENTITY;
			case "AVATAR_STORAGE_FAILED" -> HttpStatus.BAD_GATEWAY;
			case "AVATAR_STORAGE_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
			case "ACHIEVEMENT_PERSISTENCE_FAILED", "FEEDBACK_PERSISTENCE_FAILED",
					"PROFILE_GOALS_PERSISTENCE_FAILED", "IELTS_RECORDING_PERSISTENCE_FAILED",
					InterviewErrorCode.INTERVIEW_SCENE_PERSISTENCE_FAILED,
					InterviewErrorCode.INTERVIEW_REPORT_PERSISTENCE_FAILED,
					InterviewErrorCode.INTERVIEW_RECORDING_PERSISTENCE_FAILED ->
					HttpStatus.INTERNAL_SERVER_ERROR;
			case "AVATAR_DIMENSION_INVALID", "AVATAR_CONTENT_INVALID" ->
					HttpStatus.UNPROCESSABLE_ENTITY;
			case InterviewErrorCode.INTERVIEW_MATERIAL_INVALID,
					InterviewErrorCode.INTERVIEW_REQUEST_INVALID,
					InterviewErrorCode.INTERVIEW_CONTEXT_LLM_RESPONSE_INVALID,
					InterviewErrorCode.INTERVIEW_MATERIAL_LLM_RESPONSE_INVALID,
					InterviewErrorCode.INTERVIEW_TURN_OUT_OF_ORDER,
					InterviewErrorCode.INTERVIEW_AUDIO_INVALID ->
					HttpStatus.BAD_REQUEST;
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
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure("VALIDATION_ERROR", message));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(
			MaxUploadSizeExceededException exception,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(ApiResponse.failure("PAYLOAD_TOO_LARGE", "Request payload is too large"));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
			HttpMediaTypeNotSupportedException exception,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(ApiResponse.failure("MEDIA_TYPE_UNSUPPORTED", "Request media type is not supported"));
	}
}
