package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.vo.scene.ScenarioDialogueCompletionReason;
import com.unispeaking.domain.vo.scene.ScenarioDialogueStage;
import java.util.List;

public record ScenarioDialogueStateResponse(
		String sceneId,
		String sessionId,
		ScenarioDialogueStage stage,
		int effectiveUserTurns,
		int maximumUserTurns,
		List<ScenarioOutcomeState> outcomes,
		boolean completed,
		ScenarioDialogueCompletionReason completionReason,
		String controlInstruction,
		String warning) {

	public ScenarioDialogueStateResponse {
		outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
	}
}
