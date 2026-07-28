package com.unispeaking.domain.dto.session;

public record EndSessionResponse(
		String sessionId,
		String stopTime) {
}
