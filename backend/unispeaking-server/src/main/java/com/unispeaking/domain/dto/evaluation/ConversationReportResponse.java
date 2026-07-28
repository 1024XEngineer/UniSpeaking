package com.unispeaking.domain.dto.evaluation;

import com.unispeaking.domain.vo.evaluation.FiveDimensionScore;

public record ConversationReportResponse(
		String reportId,
		String localSessionId,
		Integer totalScore,
		FiveDimensionScore dimensionScore,
		String report) {
}
