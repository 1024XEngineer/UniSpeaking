package com.unispeaking.domain.dto.session;

public record SessionSocketMessage(
		String type,
		String sessionId,
		Message message,
		String stopTime,
		String providerSessionId) {
}
