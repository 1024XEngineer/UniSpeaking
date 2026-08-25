package com.unispeaking.domain.po.scene;

import com.unispeaking.domain.vo.task.AsyncTaskStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomSceneGenerationTask(
		UUID taskId,
		String userId,
		String sceneId,
		String sceneInput,
		String userPreference,
		AsyncTaskStatus status,
		String resultJson,
		String failureReason,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {
}
