package com.unispeaking.domain.dto.ai;

import com.unispeaking.domain.vo.ai.AiCallContext;
import com.unispeaking.domain.vo.evaluation.AudioInput;

public record PronunciationEvaluationRequest(
		AiCallContext context,
		AudioInput audio,
		String referenceText) {
}
