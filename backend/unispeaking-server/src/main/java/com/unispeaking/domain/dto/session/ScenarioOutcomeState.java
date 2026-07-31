package com.unispeaking.domain.dto.session;

public record ScenarioOutcomeState(
		String outcomeId,
		String description,
		String evidence,
		boolean satisfied) {
}
