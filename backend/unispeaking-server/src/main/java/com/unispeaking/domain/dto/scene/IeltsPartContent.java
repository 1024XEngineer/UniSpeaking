package com.unispeaking.domain.dto.scene;

import java.util.List;

public record IeltsPartContent(
		Integer part,
		List<String> questions,
		List<String> recommendedExpressions,
		String dialoguePrompt) {

	public IeltsPartContent {
		questions = questions == null ? List.of() : List.copyOf(questions);
		recommendedExpressions = recommendedExpressions == null
				? List.of()
				: List.copyOf(recommendedExpressions);
	}
}
