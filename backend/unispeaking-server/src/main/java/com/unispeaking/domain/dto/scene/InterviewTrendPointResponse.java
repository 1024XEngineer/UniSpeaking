package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.InterviewReportType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

public record InterviewTrendPointResponse(
		String interviewId,
		InterviewReportType reportType,
		OffsetDateTime completedAt,
		BigDecimal overallScore,
		BigDecimal fluency,
		BigDecimal logicCoherence,
		BigDecimal grammarControl,
		BigDecimal pronunciationIntelligibility,
		BigDecimal vocabularyExpression) {

	public InterviewTrendPointResponse {
		if (interviewId == null || interviewId.isBlank()) {
			throw new IllegalArgumentException("interviewId must not be blank");
		}
		if (reportType != InterviewReportType.FULL) {
			throw new IllegalArgumentException("trend points require a FULL report");
		}
		Objects.requireNonNull(completedAt, "completedAt");
		Objects.requireNonNull(overallScore, "overallScore");
		Objects.requireNonNull(fluency, "fluency");
		Objects.requireNonNull(logicCoherence, "logicCoherence");
		Objects.requireNonNull(grammarControl, "grammarControl");
		Objects.requireNonNull(pronunciationIntelligibility,
				"pronunciationIntelligibility");
		Objects.requireNonNull(vocabularyExpression, "vocabularyExpression");
	}
}
