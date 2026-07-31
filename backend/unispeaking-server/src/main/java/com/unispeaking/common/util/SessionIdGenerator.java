package com.unispeaking.common.util;

import com.unispeaking.domain.vo.scene.SceneType;
import java.util.Objects;
import java.util.UUID;

public final class SessionIdGenerator {

	private SessionIdGenerator() {
	}

	public static String generate(SceneType sceneType) {
		SceneType type = Objects.requireNonNull(
				sceneType,
				"sceneType must not be null");
		return type.sceneIdPrefix()
				+ "_session_"
				+ UUID.randomUUID().toString().replace("-", "");
	}
}
