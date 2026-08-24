package com.unispeaking.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.ErrorResponseException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatusCode;
import org.mockito.Mockito;
import com.unispeaking.admin.quality.QualityIssueTelemetrySink;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void mapsGenericObjectStorageErrors() {
		assertEquals(
				HttpStatus.BAD_GATEWAY,
				handler.handleBusinessException(new BusinessException(
						"OBJECT_STORAGE_FAILED",
						"对象存储服务暂时不可用"))
						.getStatusCode());
		assertEquals(
				HttpStatus.SERVICE_UNAVAILABLE,
				handler.handleBusinessException(new BusinessException(
						"OBJECT_STORAGE_UNAVAILABLE",
						"对象存储尚未配置"))
						.getStatusCode());
	}

	@Test
	void mapsAchievementNotFoundAndPersistenceFailures() {
		var missing = handler.handleBusinessException(new BusinessException(
				"ACHIEVEMENT_UNLOCK_NOT_FOUND",
				"成就尚未解锁"));
		var failed = handler.handleBusinessException(new BusinessException(
				"ACHIEVEMENT_PERSISTENCE_FAILED",
				"成就状态保存失败"));

		assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
		assertEquals("ACHIEVEMENT_UNLOCK_NOT_FOUND", missing.getBody().code());
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failed.getStatusCode());
		assertEquals("ACHIEVEMENT_PERSISTENCE_FAILED", failed.getBody().code());
	}

	@Test
	void keepsInvalidAcknowledgementAsBadRequest() {
		var response = handler.handleBusinessException(new BusinessException(
				"ACHIEVEMENT_ACKNOWLEDGEMENT_INVALID",
				"acknowledged 必须为 true"));

		assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
	}

	@Test
	void mapsAllImportantBusinessExceptionStatusFamilies() {
		for (String code : new String[] {
				"AUTHENTICATION_REQUIRED", "INVALID_ACCESS_TOKEN", "ACCESS_TOKEN_REVOKED",
				"INVALID_CREDENTIALS", "USER_NOT_ACTIVE", "REFRESH_TOKEN_INVALID"}) {
			assertEquals(HttpStatus.UNAUTHORIZED,
					handler.handleBusinessException(new BusinessException(code, code)).getStatusCode());
		}
		for (String code : new String[] {"SESSION_ACCESS_DENIED", "ADMIN_ACCESS_DENIED",
				InterviewErrorCode.INTERVIEW_SCENE_ACCESS_DENIED}) {
			assertEquals(HttpStatus.FORBIDDEN,
					handler.handleBusinessException(new BusinessException(code, code)).getStatusCode());
		}
		for (String code : new String[] {"FEEDBACK_NOT_FOUND", "FEEDBACK_LOOKUP_DENIED",
				"IELTS_RECORDING_NOT_FOUND", InterviewErrorCode.INTERVIEW_REPORT_NOT_FOUND,
				InterviewErrorCode.INTERVIEW_RECORDING_NOT_FOUND}) {
			assertEquals(HttpStatus.NOT_FOUND,
					handler.handleBusinessException(new BusinessException(code, code)).getStatusCode());
		}
		for (String code : new String[] {"USERNAME_ALREADY_EXISTS", "PASSWORD_UPDATE_CONFLICT",
				"PROFILE_UPDATE_CONFLICT", "FEEDBACK_UPDATE_CONFLICT",
				InterviewErrorCode.INTERVIEW_TURN_CONTENT_MISMATCH,
				InterviewErrorCode.INTERVIEW_TURN_MESSAGE_PENDING,
				InterviewErrorCode.INTERVIEW_SESSION_ENDED}) {
			assertEquals(HttpStatus.CONFLICT,
					handler.handleBusinessException(new BusinessException(code, code)).getStatusCode());
		}
		assertEquals(HttpStatus.TOO_MANY_REQUESTS,
				handler.handleBusinessException(new BusinessException(
						InterviewErrorCode.INTERVIEW_DAILY_LIMIT_REACHED, "limit")).getStatusCode());
	}

	@Test
	void mapsOcrDocumentInterviewAndStorageFailures() {
		for (String code : new String[] {"OCR_TIMEOUT"}) {
			assertEquals(HttpStatus.GATEWAY_TIMEOUT, handler.handleBusinessException(
					new BusinessException(code, code)).getStatusCode());
		}
		for (String code : new String[] {"OCR_PROCESS_FAILED", "OCR_RESPONSE_INVALID"}) {
			assertEquals(HttpStatus.BAD_GATEWAY, handler.handleBusinessException(
					new BusinessException(code, code)).getStatusCode());
		}
		for (String code : new String[] {"OCR_UNAVAILABLE", "AVATAR_STORAGE_UNAVAILABLE"}) {
			assertEquals(HttpStatus.SERVICE_UNAVAILABLE, handler.handleBusinessException(
					new BusinessException(code, code)).getStatusCode());
		}
		for (String code : new String[] {"OCR_INPUT_REQUIRED", "OCR_TOO_MANY_IMAGES",
				"OCR_TOTAL_SIZE_EXCEEDED", "OCR_PIXEL_LIMIT_EXCEEDED", "DOCUMENT_INPUT_REQUIRED",
				"DOCUMENT_TOO_LARGE", "DOCUMENT_PDF_PAGE_LIMIT_EXCEEDED", "DOCUMENT_TEXT_EMPTY",
				"DOCUMENT_TEXT_TOO_LARGE", InterviewErrorCode.INTERVIEW_MATERIAL_INVALID,
				InterviewErrorCode.INTERVIEW_REQUEST_INVALID, InterviewErrorCode.INTERVIEW_AUDIO_INVALID}) {
			assertEquals(HttpStatus.BAD_REQUEST, handler.handleBusinessException(
					new BusinessException(code, code)).getStatusCode());
		}
		for (String code : new String[] {"OCR_FORMAT_UNSUPPORTED", "OCR_CONTENT_INVALID",
				"DOCUMENT_FORMAT_UNSUPPORTED", "DOCUMENT_CONTENT_INVALID", "AVATAR_CONTENT_INVALID"}) {
			assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, handler.handleBusinessException(
					new BusinessException(code, code)).getStatusCode());
		}
		for (String code : new String[] {"AVATAR_STORAGE_FAILED", "OBJECT_STORAGE_FAILED"}) {
			assertEquals(HttpStatus.BAD_GATEWAY, handler.handleBusinessException(
					new BusinessException(code, code)).getStatusCode());
		}
	}

	@Test
	void handlesUnexpectedFrameworkValidationEmailAndTelemetryFailures() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
		var unexpected = handler.handleUnexpectedException(new IllegalStateException("boom"), request);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, unexpected.getStatusCode());
		assertEquals("INTERNAL_SERVER_ERROR", unexpected.getBody().code());

		ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "");
		ErrorResponseException framework = new ErrorResponseException(
				HttpStatusCode.valueOf(400), detail, null);
		var frameworkResponse = handler.handleFrameworkStatusException(framework, request);
		assertEquals(HttpStatus.BAD_REQUEST, frameworkResponse.getStatusCode());
		assertEquals("HTTP_400", frameworkResponse.getBody().code());

		assertEquals(HttpStatus.UNAUTHORIZED, handler.handleEmailAuthException(
				new EmailAuthException("UNAUTHENTICATED")).getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, handler.handleEmailAuthException(
				new EmailAuthException("OTHER")).getStatusCode());
		var errors = validationException("acceptGeneric", new Object());
		assertEquals(HttpStatus.BAD_REQUEST, handler.handleValidationException(errors).getStatusCode());
	}

	@Test
	void reportingFailureDoesNotChangeUnexpectedResponse() {
		var sink = Mockito.mock(QualityIssueTelemetrySink.class);
		Mockito.doThrow(new IllegalStateException("telemetry down")).when(sink)
				.captureBackend(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt(), Mockito.any());
		var response = new GlobalExceptionHandler(sink).handleUnexpectedException(
				new RuntimeException("boom"), new MockHttpServletRequest("POST", "/api/fail"));
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
		assertTrue(response.getBody().code().contains("INTERNAL"));
	}

	@Test
	void mapsMultipartFailuresBeforeControllerInvocation() {
		MockHttpServletRequest genericRequest = new MockHttpServletRequest(
				"POST", "/api/profile/avatar");

		var genericTooLarge = handler.handleUploadTooLarge(
				new MaxUploadSizeExceededException(10), genericRequest);
		var genericMedia = handler.handleUnsupportedMediaType(
				new HttpMediaTypeNotSupportedException("application/xml"),
				genericRequest);

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, genericTooLarge.getStatusCode());
		assertEquals("PAYLOAD_TOO_LARGE", genericTooLarge.getBody().code());
		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, genericMedia.getStatusCode());
		assertEquals("MEDIA_TYPE_UNSUPPORTED", genericMedia.getBody().code());
	}

	private MethodArgumentNotValidException validationException(
			String methodName,
			Object target) throws Exception {
		Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod(
				methodName, target.getClass());
		BeanPropertyBindingResult errors = new BeanPropertyBindingResult(target, "request");
		errors.addError(new FieldError("request", "field", "is invalid"));
		return new MethodArgumentNotValidException(new MethodParameter(method, 0), errors);
	}

	@SuppressWarnings("unused")
	private void acceptGeneric(Object request) {
	}
}
