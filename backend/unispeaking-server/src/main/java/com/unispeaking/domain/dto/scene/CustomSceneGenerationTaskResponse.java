package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.task.AsyncTaskStatus;
import java.util.UUID;

public record CustomSceneGenerationTaskResponse(
		UUID taskId,
		String sceneId,
		AsyncTaskStatus status,
		CustomSceneGenerationResponse result,
		String failureReason) {
}
