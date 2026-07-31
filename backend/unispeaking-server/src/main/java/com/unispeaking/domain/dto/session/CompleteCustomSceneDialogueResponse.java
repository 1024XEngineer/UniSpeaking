package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.dto.evaluation.DialogueReportResult;

public record CompleteCustomSceneDialogueResponse(
		String sceneId,
		String sessionId,
		String stopTime,
		DialogueReportResult evaluation,
		ScenarioDialogueStateResponse state) {
}
