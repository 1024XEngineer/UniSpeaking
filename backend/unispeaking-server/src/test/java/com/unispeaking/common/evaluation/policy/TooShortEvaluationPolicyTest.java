package com.unispeaking.common.evaluation.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TooShortEvaluationPolicyTest {

	@Test
	void createsRecognizableZeroScoreResult() {
		var result = TooShortEvaluationPolicy.createResult(3, "Hi");

		assertEquals(3, result.turnNo());
		assertEquals("Hi", result.transcript());
		assertTrue(result.words().isEmpty());
		assertTrue(TooShortEvaluationPolicy.isTooShort(result));
	}

	@Test
	void requiresEveryZeroScoreAndExactFeedback() {
		assertFalse(TooShortEvaluationPolicy.isTooShort(null));
		assertFalse(TooShortEvaluationPolicy.isTooShort(
				BigDecimal.ONE,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				TooShortEvaluationPolicy.FEEDBACK_SUMMARY));
		assertFalse(TooShortEvaluationPolicy.isTooShort(
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				" 过短，不予评分 "));
		assertFalse(TooShortEvaluationPolicy.isTooShort(
				null,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				TooShortEvaluationPolicy.FEEDBACK_SUMMARY));
	}
}
