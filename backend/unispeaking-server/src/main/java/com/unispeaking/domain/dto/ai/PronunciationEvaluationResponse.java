package com.unispeaking.domain.dto.ai;

public record PronunciationEvaluationResponse(
		Integer totalScore,
		Integer fluency,
		Integer pronunciation,
		Integer rhythm,
		Integer tone) {
}
