package com.unispeaking.common.exception.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 验证评分错误码的稳定性及异常对外消息边界。
 */
class EvaluationExceptionTest {

	@Test
	void usesDefaultMessageFromErrorCode() {
		EvaluationException exception =
				new EvaluationException(EvaluationErrorCode.AUDIO_INVALID);

		assertEquals("EVALUATION_AUDIO_INVALID", exception.code());
		assertEquals(
				"Audio is not a valid 16 kHz mono 16-bit PCM WAV file",
				exception.getMessage());
		assertSame(EvaluationErrorCode.AUDIO_INVALID, exception.errorCode());
	}

	@Test
	void preservesSafeMessageAndCauseWithoutExposingCauseText() {
		IllegalStateException cause =
				new IllegalStateException("jdbc:postgresql://secret-host/database");
		EvaluationException exception = new EvaluationException(
				EvaluationErrorCode.PERSISTENCE_FAILED,
				"Unable to save evaluation result",
				cause);

		assertEquals("Unable to save evaluation result", exception.getMessage());
		assertSame(cause, exception.getCause());
		assertFalse(exception.getMessage().contains("secret-host"));
	}

	@Test
	void fallsBackToDefaultMessageWhenCustomMessageIsBlank() {
		EvaluationException exception = new EvaluationException(
				EvaluationErrorCode.PROVIDER_CALL_FAILED,
				"   ");

		assertEquals(
				EvaluationErrorCode.PROVIDER_CALL_FAILED.defaultMessage(),
				exception.getMessage());
	}

	@Test
	void exposesUniqueEvaluationPrefixedCodes() {
		Set<String> codes = Arrays.stream(EvaluationErrorCode.values())
				.map(EvaluationErrorCode::code)
				.collect(Collectors.toSet());

		assertEquals(EvaluationErrorCode.values().length, codes.size());
		assertTrue(codes.stream().allMatch(code -> code.startsWith("EVALUATION_")));
	}
}
