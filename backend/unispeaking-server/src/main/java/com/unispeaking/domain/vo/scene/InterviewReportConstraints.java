package com.unispeaking.domain.vo.scene;

import java.math.BigDecimal;

final class InterviewReportConstraints {

	static final int MAX_SUMMARY_CODE_POINTS = 2_000;
	static final int MAX_FEEDBACK_CODE_POINTS = 1_000;

	private static final BigDecimal MAX_SCORE = new BigDecimal("100");
	private static final int MIN_SCORE_SCALE = -2;
	private static final int MAX_SCORE_SCALE = 8;
	private static final int MAX_SCORE_PRECISION = 11;

	private InterviewReportConstraints() {
	}

	static BigDecimal requireScore(BigDecimal score, String fieldName) {
		if (score == null) {
			throw new IllegalArgumentException(fieldName + " must not be null");
		}
		if (score.scale() < MIN_SCORE_SCALE || score.scale() > MAX_SCORE_SCALE) {
			throw new IllegalArgumentException(
					fieldName + " scale must be between "
							+ MIN_SCORE_SCALE + " and " + MAX_SCORE_SCALE);
		}
		if (score.precision() > MAX_SCORE_PRECISION) {
			throw new IllegalArgumentException(
					fieldName + " precision must not exceed " + MAX_SCORE_PRECISION);
		}
		if (score.compareTo(BigDecimal.ZERO) < 0
				|| score.compareTo(MAX_SCORE) > 0) {
			throw new IllegalArgumentException(
					fieldName + " must be between 0 and 100");
		}
		return score;
	}

	static String requireText(
			String value,
			String fieldName,
			int maxCodePoints) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		if (value.codePointCount(0, value.length()) > maxCodePoints) {
			throw new IllegalArgumentException(
					fieldName + " must not exceed " + maxCodePoints + " characters");
		}
		return value;
	}
}
