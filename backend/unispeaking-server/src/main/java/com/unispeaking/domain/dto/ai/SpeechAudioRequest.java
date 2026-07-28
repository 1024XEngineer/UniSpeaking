package com.unispeaking.domain.dto.ai;

import com.unispeaking.domain.vo.ai.AiCallContext;

public record SpeechAudioRequest(AiCallContext context, String text) {
}
