package com.unispeaking.domain.po.scene;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ScenarioSuccessFactor(
		int minimumUserTurns,
		int maximumUserTurns,
		Map<String, String> requiredOutcomes,
		String stopWhen,
		String closingInstruction) {

	public static final int HARD_MAXIMUM_USER_TURNS = 10;

	public ScenarioSuccessFactor {
		minimumUserTurns = Math.max(1, Math.min(
				minimumUserTurns,
				HARD_MAXIMUM_USER_TURNS));
		maximumUserTurns = Math.max(
				minimumUserTurns,
				Math.min(maximumUserTurns, HARD_MAXIMUM_USER_TURNS));
		requiredOutcomes = Collections.unmodifiableMap(
				new LinkedHashMap<>(requiredOutcomes));
		stopWhen = stopWhen == null ? "" : stopWhen.trim();
		closingInstruction = closingInstruction == null
				? ""
				: closingInstruction.trim();
	}
}
