package com.unispeaking.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
