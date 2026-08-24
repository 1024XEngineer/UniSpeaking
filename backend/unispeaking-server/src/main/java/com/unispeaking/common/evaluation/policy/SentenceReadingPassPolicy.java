package com.unispeaking.common.evaluation.policy;

import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import java.math.BigDecimal;
import java.util.Objects;

/** Decides whether a sentence reading is good enough to continue practice. */
public final class SentenceReadingPassPolicy {

	private static final BigDecimal STANDARD_PASS_SCORE = score("80");
	private static final BigDecimal NEAR_PASS_SCORE = score("70");
	private static final BigDecimal MINIMUM_PRONUNCIATION = score("75");
	private static final BigDecimal MINIMUM_INTEGRITY = score("80");
	private static final BigDecimal REPEATED_BEST_PASS_SCORE = score("75");
	private static final long MINIMUM_REPEATED_ATTEMPTS = 3;

	public boolean passes(PronunciationAssessmentResult assessment) {
		Objects.requireNonNull(assessment, "assessment must not be null");
		if (atLeast(assessment.overallScore(), STANDARD_PASS_SCORE)) {
			return true;
		}
		return atLeast(assessment.overallScore(), NEAR_PASS_SCORE)
				&& atLeast(assessment.pronunciationScore(), MINIMUM_PRONUNCIATION)
				&& atLeast(assessment.integrityScore(), MINIMUM_INTEGRITY);
	}

	public boolean passesRepeatedBest(long attemptCount, BigDecimal bestScore) {
		return attemptCount >= MINIMUM_REPEATED_ATTEMPTS
				&& atLeast(bestScore, REPEATED_BEST_PASS_SCORE);
	}

	private boolean atLeast(BigDecimal value, BigDecimal threshold) {
		return value != null && value.compareTo(threshold) >= 0;
	}

	private static BigDecimal score(String value) {
		return new BigDecimal(value);
	}
}
