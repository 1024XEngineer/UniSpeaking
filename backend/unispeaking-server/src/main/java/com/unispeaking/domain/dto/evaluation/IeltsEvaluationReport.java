package com.unispeaking.domain.dto.evaluation;

import java.math.BigDecimal;

public record IeltsEvaluationReport(
		BigDecimal fluencyAndCoherence,
		BigDecimal lexicalResource,
		BigDecimal grammaticalRangeAndAccuracy,
		BigDecimal pronunciation,
		BigDecimal bandScore,
		String summary) {
}
