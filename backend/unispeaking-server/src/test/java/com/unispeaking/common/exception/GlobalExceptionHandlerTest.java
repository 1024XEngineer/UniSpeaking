package com.unispeaking.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.unispeaking.domain.dto.scene.CreateInterviewRequest;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
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
	void mapsOnlyInterviewValidationErrorsToUnprocessableEntity() throws Exception {
		CreateInterviewRequest request = new CreateInterviewRequest(
				"", InterviewDifficulty.STANDARD, "", null);
		var interviewError = validationException("acceptInterview", request);
		var genericError = validationException("acceptGeneric", new Object());

		var interviewResponse = handler.handleValidationException(interviewError);
		var genericResponse = handler.handleValidationException(genericError);

		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, interviewResponse.getStatusCode());
		assertEquals("INTERVIEW_INPUT_INVALID", interviewResponse.getBody().code());
		assertEquals(HttpStatus.BAD_REQUEST, genericResponse.getStatusCode());
		assertEquals("VALIDATION_ERROR", genericResponse.getBody().code());
	}

	@Test
	void mapsInterviewMultipartFailuresBeforeControllerInvocation() {
		MockHttpServletRequest interviewRequest = new MockHttpServletRequest(
				"POST", "/api/interviews");
		MockHttpServletRequest genericRequest = new MockHttpServletRequest(
				"POST", "/api/profile/avatar");

		var interviewTooLarge = handler.handleUploadTooLarge(
				new MaxUploadSizeExceededException(10), interviewRequest);
		var genericTooLarge = handler.handleUploadTooLarge(
				new MaxUploadSizeExceededException(10), genericRequest);
		var interviewMedia = handler.handleUnsupportedMediaType(
				new HttpMediaTypeNotSupportedException("application/xml"),
				interviewRequest);
		var genericMedia = handler.handleUnsupportedMediaType(
				new HttpMediaTypeNotSupportedException("application/xml"),
				genericRequest);

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, interviewTooLarge.getStatusCode());
		assertEquals("INTERVIEW_PAYLOAD_TOO_LARGE", interviewTooLarge.getBody().code());
		assertEquals("PAYLOAD_TOO_LARGE", genericTooLarge.getBody().code());
		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, interviewMedia.getStatusCode());
		assertEquals("INTERVIEW_MEDIA_TYPE_UNSUPPORTED", interviewMedia.getBody().code());
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
	private void acceptInterview(CreateInterviewRequest request) {
	}

	@SuppressWarnings("unused")
	private void acceptGeneric(Object request) {
	}
}
