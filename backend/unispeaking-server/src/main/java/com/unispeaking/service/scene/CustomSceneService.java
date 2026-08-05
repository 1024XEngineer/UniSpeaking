package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.CustomSceneGenerationResponse;
import com.unispeaking.domain.dto.scene.CustomSceneRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.dto.session.CompleteCustomSceneDialogueResponse;
import com.unispeaking.domain.dto.session.ScenarioDialogueStateResponse;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;

public interface CustomSceneService extends SceneService<
		CustomSceneRequest,
		CustomSceneGenerationResponse> {

	SceneGenerationResponse generateScene(SceneGenerationRequest request);

	byte[] synthesizeSpeech(String sceneId, String text, String model);

	TranslateTextResponse translate(String sceneId, String text);

	StartSceneSessionResponse startSession(
			String sceneId,
			StartCustomSceneDialogueRequest request);

	CompleteCustomSceneDialogueResponse completeSession(
			String sceneId,
			String sessionId,
			String stopTime);

	ScenarioDialogueStateResponse advanceSessionState(
			String sceneId,
			String sessionId,
			int turnNo,
			String transcript);

	ScenarioDialogueStateResponse getSessionState(
			String sceneId,
			String sessionId);
}
