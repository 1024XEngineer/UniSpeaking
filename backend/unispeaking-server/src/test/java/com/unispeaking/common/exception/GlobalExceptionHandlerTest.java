package com.unispeaking.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
}
