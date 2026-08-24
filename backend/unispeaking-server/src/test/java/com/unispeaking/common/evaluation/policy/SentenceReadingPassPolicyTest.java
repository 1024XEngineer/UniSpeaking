package com.unispeaking.common.evaluation.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SentenceReadingPassPolicyTest {

	private final SentenceReadingPassPolicy policy =
			new SentenceReadingPassPolicy();

	@Test
	void acceptsTheRealContractSentenceNearPassInsteadOfBlockingForever() {
		PronunciationAssessmentResult assessment = assessment(
				"76.40",
				"83.70",
				"90.40",
				"90.00");

		assertTrue(policy.passes(assessment));
	}

	@Test
	void keepsLowOrIncompleteReadingsBelowThePassLine() {
		assertFalse(policy.passes(assessment("69.90", "90", "90", "90")));
		assertFalse(policy.passes(assessment("75", "90", "90", "70")));
	}

	@Test
	void preservesTheExistingEightyPointPassRule() {
		assertTrue(policy.passes(assessment("80", "60", "60", "60")));
	}

	@Test
	void allowsARepeatedNearPassWhenTheBestOfThreeAttemptsReachedSeventyFive() {
		assertTrue(policy.passesRepeatedBest(
				3,
				new BigDecimal("76.40")));
		assertFalse(policy.passesRepeatedBest(
				2,
				new BigDecimal("79.90")));
	}

	private PronunciationAssessmentResult assessment(
			String overall,
			String pronunciation,
			String fluency,
			String integrity) {
		return new PronunciationAssessmentResult(
				new BigDecimal(overall),
				new BigDecimal("80"),
				null,
				new BigDecimal(integrity),
				new BigDecimal(pronunciation),
				new BigDecimal(fluency),
				EndingTone.FALL,
				List.of());
	}
}
