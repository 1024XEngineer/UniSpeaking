package com.unispeaking.domain.dto.evaluation;

import com.unispeaking.domain.vo.evaluation.AudioInput;

public record DialogueTurnEvaluationRequest(
		String userId,
		String localSessionId,
		String turnId,
		AudioInput audio,
		String userText) {
}
