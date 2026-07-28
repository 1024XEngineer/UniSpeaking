package com.unispeaking.domain.dto.ai;

import com.unispeaking.domain.vo.ai.AiCallContext;
import com.unispeaking.domain.vo.evaluation.AudioInput;

public record AudioTranscriptionRequest(AiCallContext context, AudioInput audio) {
}
