package com.unispeaking.domain.dto.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record SentenceEvaluationResponse(
		BigDecimal overallScore,
		Boolean passed,
		List<WordPronunciationScore> words) {

	public SentenceEvaluationResponse {
		overallScore = Objects.requireNonNull(
				overallScore,
				"overallScore must not be null");
		passed = Objects.requireNonNull(passed, "passed must not be null");
		words = List.copyOf(Objects.requireNonNull(
				words,
				"words must not be null"));
	}
}
