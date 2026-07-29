package com.unispeaking.infrastructure.persistence.evaluation.result;

import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code practice_results} 表中的五维评分与总分。
 */
public record PracticeResultScores(
		UUID sessionId,
		BigDecimal accuracyScore,
		BigDecimal fluencyScore,
		BigDecimal grammarScore,
		BigDecimal vocabularyScore,
		BigDecimal naturalnessScore,
		BigDecimal finalScore) {

	private static final BigDecimal MAX_SCORE = new BigDecimal("100");

	public PracticeResultScores {
		sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
		accuracyScore = requireScore(accuracyScore, "accuracyScore");
		fluencyScore = requireScore(fluencyScore, "fluencyScore");
		grammarScore = requireScore(grammarScore, "grammarScore");
		vocabularyScore = requireScore(vocabularyScore, "vocabularyScore");
		naturalnessScore = requireScore(naturalnessScore, "naturalnessScore");
		finalScore = requireScore(finalScore, "finalScore");
	}

	public static PracticeResultScores from(
			UUID sessionId,
			DialogueReportResult report) {
		DialogueReportResult requiredReport =
				Objects.requireNonNull(report, "report must not be null");
		return new PracticeResultScores(
				sessionId,
				requiredReport.accuracyScore(),
				requiredReport.fluencyScore(),
				requiredReport.grammarScore(),
				requiredReport.vocabularyScore(),
				requiredReport.naturalnessScore(),
				requiredReport.finalScore());
	}

	private static BigDecimal requireScore(
			BigDecimal score,
			String fieldName) {
		BigDecimal value = Objects.requireNonNull(
				score,
				fieldName + " must not be null");
		if (value.compareTo(BigDecimal.ZERO) < 0
				|| value.compareTo(MAX_SCORE) > 0
				|| Math.max(0, value.stripTrailingZeros().scale()) > 1) {
			throw new IllegalArgumentException(
					fieldName + " must be a 0-100 score with at most one decimal place");
		}
		return value;
	}
}
