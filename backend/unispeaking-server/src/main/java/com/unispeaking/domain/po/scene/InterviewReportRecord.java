package com.unispeaking.domain.po.scene;

import com.unispeaking.domain.vo.scene.InterviewReportDimension;
import com.unispeaking.domain.vo.scene.InterviewReportType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Persisted complete five-dimension Interview report.
 */
public record InterviewReportRecord(
		String interviewId,
		InterviewReportType reportType,
		BigDecimal overallScore,
		String overallSummary,
		InterviewReportDimension fluency,
		InterviewReportDimension logicCoherence,
		InterviewReportDimension grammarControl,
		InterviewReportDimension pronunciationIntelligibility,
		InterviewReportDimension vocabularyExpression,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	private static final BigDecimal MAX_SCORE = new BigDecimal("100");

	public InterviewReportRecord {
		if (interviewId == null || interviewId.isBlank()) {
			throw new IllegalArgumentException("interviewId must not be blank");
		}
		Objects.requireNonNull(reportType, "reportType must not be null");
		if (overallScore == null
				|| overallScore.compareTo(BigDecimal.ZERO) < 0
				|| overallScore.compareTo(MAX_SCORE) > 0) {
			throw new IllegalArgumentException(
					"overallScore must be between 0 and 100");
		}
		if (overallSummary == null || overallSummary.isBlank()) {
			throw new IllegalArgumentException("overallSummary must not be blank");
		}
		Objects.requireNonNull(fluency, "fluency must not be null");
		Objects.requireNonNull(logicCoherence,
				"logicCoherence must not be null");
		Objects.requireNonNull(grammarControl,
				"grammarControl must not be null");
		Objects.requireNonNull(pronunciationIntelligibility,
				"pronunciationIntelligibility must not be null");
		Objects.requireNonNull(vocabularyExpression,
				"vocabularyExpression must not be null");
		Objects.requireNonNull(createdAt, "createdAt must not be null");
		Objects.requireNonNull(updatedAt, "updatedAt must not be null");
	}
}
