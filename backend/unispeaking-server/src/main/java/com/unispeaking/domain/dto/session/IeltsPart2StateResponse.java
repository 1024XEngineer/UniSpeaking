package com.unispeaking.domain.dto.session;

public record IeltsPart2StateResponse(
		String sceneId,
		String sessionId,
		String phase,
		boolean completed,
		String controlInstruction) {
}
