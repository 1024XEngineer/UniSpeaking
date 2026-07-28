package com.unispeaking.domain.dto.evaluation;

public record DialogueTurnEvaluationResponse(
		String turnId,
		Integer totalScore,
		Integer fluency,
		Integer pronunciation,
		Integer rhythm,
		Integer tone,
		Integer grammar,
		Integer vocabulary,
		Integer relevance) {
}
