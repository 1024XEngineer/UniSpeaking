package com.unispeaking.domain.vo.scene;

import java.math.BigDecimal;

/**
 * Score and feedback persisted for one Interview report dimension.
 */
public record InterviewReportDimension(
		BigDecimal score,
		String evaluation,
		String actionSuggestion) {

	private static final BigDecimal MAX_SCORE = new BigDecimal("100");

	public InterviewReportDimension {
		if (score == null
				|| score.compareTo(BigDecimal.ZERO) < 0
				|| score.compareTo(MAX_SCORE) > 0) {
			throw new IllegalArgumentException(
					"score must be between 0 and 100");
		}
		requireText(evaluation, "evaluation");
		requireText(actionSuggestion, "actionSuggestion");
	}

	private static void requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
	}
}
