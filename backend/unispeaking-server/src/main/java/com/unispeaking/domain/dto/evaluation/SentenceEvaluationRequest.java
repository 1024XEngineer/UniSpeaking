package com.unispeaking.domain.dto.evaluation;

import com.unispeaking.domain.vo.evaluation.AudioInput;

public record SentenceEvaluationRequest(
		String userId,
		String contentId,
		AudioInput audio) {
}
