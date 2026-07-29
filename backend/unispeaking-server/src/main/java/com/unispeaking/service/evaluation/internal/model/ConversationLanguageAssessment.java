package com.unispeaking.service.evaluation.internal.model;

import java.math.BigDecimal;
import java.util.List;

public record ConversationLanguageAssessment(
		BigDecimal grammarScore,
		BigDecimal vocabularyScore,
		BigDecimal textNaturalnessScore,
		String summary,
		List<String> strengths,
		List<String> improvements) {
}
