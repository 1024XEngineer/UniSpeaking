package com.unispeaking.domain.po.scene;

import java.time.OffsetDateTime;
import java.util.Objects;

public record SceneAssetSnapshot(
		CustomSceneDefinition definition,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	public SceneAssetSnapshot {
		Objects.requireNonNull(definition, "definition must not be null");
		Objects.requireNonNull(createdAt, "createdAt must not be null");
		Objects.requireNonNull(updatedAt, "updatedAt must not be null");
	}
}
