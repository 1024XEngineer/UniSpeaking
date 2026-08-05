package com.unispeaking.domain.vo.scene;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Complete five-dimension Interview report produced without persistence or
 * provider calls.
 */
public record InterviewReportCalculation(
		InterviewReportType reportType,
		BigDecimal overallScore,
		String overallSummary,
		InterviewReportDimension fluency,
		InterviewReportDimension logicCoherence,
		InterviewReportDimension grammarControl,
		InterviewReportDimension pronunciationIntelligibility,
		InterviewReportDimension vocabularyExpression) {

	public InterviewReportCalculation {
		Objects.requireNonNull(reportType, "reportType must not be null");
		InterviewReportConstraints.requireScore(overallScore, "overallScore");
		overallSummary = InterviewReportConstraints.requireText(
				overallSummary,
				"overallSummary",
				InterviewReportConstraints.MAX_SUMMARY_CODE_POINTS);
		Objects.requireNonNull(fluency, "fluency must not be null");
		Objects.requireNonNull(logicCoherence, "logicCoherence must not be null");
		Objects.requireNonNull(grammarControl, "grammarControl must not be null");
		Objects.requireNonNull(
				pronunciationIntelligibility,
				"pronunciationIntelligibility must not be null");
		Objects.requireNonNull(
				vocabularyExpression,
				"vocabularyExpression must not be null");
	}
}
