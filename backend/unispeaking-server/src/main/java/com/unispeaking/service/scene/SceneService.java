package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;

public interface SceneService {
	SceneGenerationResponse generateScene(SceneGenerationRequest request);
}
