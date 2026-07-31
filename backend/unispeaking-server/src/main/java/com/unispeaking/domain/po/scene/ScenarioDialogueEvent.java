package com.unispeaking.domain.po.scene;

import com.unispeaking.domain.vo.scene.ScenarioDialogueEventType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ScenarioDialogueEvent(
		ScenarioDialogueEventType type,
		Map<String, String> outcomeValues,
		double confidence) {

	public ScenarioDialogueEvent {
		type = type == null ? ScenarioDialogueEventType.NONE : type;
		outcomeValues = outcomeValues == null
				? Map.of()
				: Collections.unmodifiableMap(
						new LinkedHashMap<>(outcomeValues));
		confidence = Math.max(0, Math.min(confidence, 1));
	}

	public static ScenarioDialogueEvent none() {
		return new ScenarioDialogueEvent(
				ScenarioDialogueEventType.NONE,
				Map.of(),
				0);
	}
}
