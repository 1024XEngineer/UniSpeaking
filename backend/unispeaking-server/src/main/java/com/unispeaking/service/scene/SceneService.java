package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.CustomSceneRequest;
import com.unispeaking.domain.dto.scene.CustomSceneGenerationResponse;
import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;

public interface SceneService {
	SceneGenerationResponse generateScene(SceneGenerationRequest request);

	CustomSceneGenerationResponse generateCustomScene(CustomSceneRequest request);

	byte[] synthesizeSpeech(String sceneId, String text, String model);

	TranslateTextResponse translate(String sceneId, String text);
}
