package com.unispeaking.domain.vo.scene;

import java.util.Arrays;
import java.util.Optional;

public enum SceneType {
	FREE_CHAT("freechat"),
	CUSTOM_SCENE("custom"),
	INTERVIEW_SCENE("interview"),
	IELTS_SCENE("ielts");

	private final String sceneIdPrefix;

	SceneType(String sceneIdPrefix) {
		this.sceneIdPrefix = sceneIdPrefix;
	}

	public String sceneIdPrefix() {
		return sceneIdPrefix;
	}

	public static Optional<SceneType> fromSceneId(String sceneId) {
		if (sceneId == null || sceneId.isBlank()) {
			return Optional.empty();
		}
		return Arrays.stream(values())
				.filter(type -> sceneId.startsWith(type.sceneIdPrefix + "_"))
				.findFirst();
	}
}
