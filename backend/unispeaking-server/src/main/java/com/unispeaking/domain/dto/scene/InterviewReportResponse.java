package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.InterviewReportType;
import java.math.BigDecimal;
import java.util.Objects;

public record InterviewReportResponse(
		InterviewReportType reportType,
		BigDecimal overallScore,
		String overallSummary,
		InterviewReportDimensionResponse fluency,
		InterviewReportDimensionResponse logicCoherence,
		InterviewReportDimensionResponse grammarControl,
		InterviewReportDimensionResponse pronunciationIntelligibility,
		InterviewReportDimensionResponse vocabularyExpression) {

	public InterviewReportResponse {
		Objects.requireNonNull(reportType, "reportType");
		Objects.requireNonNull(overallScore, "overallScore");
		if (overallSummary == null || overallSummary.isBlank()) {
			throw new IllegalArgumentException("overallSummary must not be blank");
		}
		Objects.requireNonNull(fluency, "fluency");
		Objects.requireNonNull(logicCoherence, "logicCoherence");
		Objects.requireNonNull(grammarControl, "grammarControl");
		Objects.requireNonNull(pronunciationIntelligibility,
				"pronunciationIntelligibility");
		Objects.requireNonNull(vocabularyExpression, "vocabularyExpression");
	}
}
