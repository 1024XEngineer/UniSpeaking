package com.unispeaking.domain.dto.ai;

public record SpeechAudioResponse(byte[] audioData, String audioFormat, String contentType) {
}
