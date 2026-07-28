package com.unispeaking.domain.dto.session;

public record AddSessionMessageRequest(
		String sessionId,
		Message message) {
}
