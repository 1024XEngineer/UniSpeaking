package com.unispeaking.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InterviewGlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void mapsInterviewSceneNotFoundAndAccessDenied() {
		var notFound = handler.handleBusinessException(new BusinessException(
				InterviewErrorCode.INTERVIEW_SCENE_NOT_FOUND,
				"面试场景不存在"));
		var denied = handler.handleBusinessException(new BusinessException(
				InterviewErrorCode.INTERVIEW_SCENE_ACCESS_DENIED,
				"无权访问该场景"));

		assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
		assertEquals(InterviewErrorCode.INTERVIEW_SCENE_NOT_FOUND, notFound.getBody().code());
		assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
		assertEquals(InterviewErrorCode.INTERVIEW_SCENE_ACCESS_DENIED, denied.getBody().code());
	}

	@Test
	void mapsInterviewMaterialAndRequestErrorsToBadRequest() {
		var material = handler.handleBusinessException(new BusinessException(
				InterviewErrorCode.INTERVIEW_MATERIAL_INVALID,
				"岗位职责不能为空"));
		var request = handler.handleBusinessException(new BusinessException(
				InterviewErrorCode.INTERVIEW_REQUEST_INVALID,
				"面试难度不能为空"));
		var llm = handler.handleBusinessException(new BusinessException(
				InterviewErrorCode.INTERVIEW_CONTEXT_LLM_RESPONSE_INVALID,
				"模型返回结构不完整"));

		assertEquals(HttpStatus.BAD_REQUEST, material.getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, request.getStatusCode());
		assertEquals(HttpStatus.BAD_REQUEST, llm.getStatusCode());
	}

	@Test
	void mapsInterviewPersistenceFailureToInternalServerError() {
		var failed = handler.handleBusinessException(new BusinessException(
				InterviewErrorCode.INTERVIEW_SCENE_PERSISTENCE_FAILED,
				"面试场景保存失败"));

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failed.getStatusCode());
		assertEquals(
				InterviewErrorCode.INTERVIEW_SCENE_PERSISTENCE_FAILED,
				failed.getBody().code());
	}
}
