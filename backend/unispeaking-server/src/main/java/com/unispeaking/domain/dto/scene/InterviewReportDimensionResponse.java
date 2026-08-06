package com.unispeaking.domain.dto.scene;

import java.math.BigDecimal;
import java.util.Objects;

public record InterviewReportDimensionResponse(
		BigDecimal score,
		String evaluation,
		String actionSuggestion) {

	public InterviewReportDimensionResponse {
		Objects.requireNonNull(score, "score");
		requireText(evaluation, "evaluation");
		requireText(actionSuggestion, "actionSuggestion");
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
	}
}
