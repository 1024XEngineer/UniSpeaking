package com.unispeaking.common.evaluation.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnavailableTurnEvaluationPolicyTest {

	@Test
	void createsDurableUnscoredTurn() {
		var result = UnavailableTurnEvaluationPolicy.createResult(
				4,
				"Hello there");

		assertEquals(4, result.turnNo());
		assertEquals("Hello there", result.transcript());
		assertEquals(
				UnavailableTurnEvaluationPolicy.FEEDBACK_SUMMARY,
				result.feedbackSummary());
		assertTrue(UnavailableTurnEvaluationPolicy.isUnavailable(
				new UnavailableTurnEvaluationPolicy.CustomScores(
						result.overallScore(),
						result.rhythmScore(),
						result.toneScore(),
						result.integrityScore(),
						result.pronunciationScore(),
						result.fluencyScore(),
						result.feedbackSummary())));
	}
}
