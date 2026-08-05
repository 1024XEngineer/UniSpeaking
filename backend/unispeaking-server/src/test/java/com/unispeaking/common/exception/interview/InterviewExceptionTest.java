package com.unispeaking.common.exception.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.unispeaking.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;

class InterviewExceptionTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void mapsEveryStableInterviewErrorToItsDeclaredHttpStatus() {
		for (InterviewErrorCode errorCode : InterviewErrorCode.values()) {
			var response = handler.handleBusinessException(new InterviewException(errorCode));

			assertEquals(errorCode.status(), response.getStatusCode());
			assertEquals(errorCode.code(), response.getBody().code());
			assertEquals(errorCode.defaultMessage(), response.getBody().message());
		}
	}
}
