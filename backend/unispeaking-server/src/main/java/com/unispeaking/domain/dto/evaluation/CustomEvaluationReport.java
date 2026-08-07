package com.unispeaking.domain.dto.evaluation;

import java.math.BigDecimal;

public record CustomEvaluationReport(
		BigDecimal pronunciationScore,
		BigDecimal fluencyScore,
		BigDecimal grammarScore,
		BigDecimal vocabularyScore,
		BigDecimal communicationScore,
		BigDecimal finalScore,
		String summary) {
}
