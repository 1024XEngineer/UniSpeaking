package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.StartFreeChatRequest;
import com.unispeaking.domain.dto.session.CompleteCustomSceneDialogueResponse;
import com.unispeaking.domain.dto.session.ScenarioDialogueStateResponse;
import com.unispeaking.domain.dto.session.StartCustomSceneDialogueRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.vo.scene.SceneType;

public interface SessionService {

	StartSessionResponse startSession(SceneType sceneType, String prompt);

	StartSceneSessionResponse startFreeChat(StartFreeChatRequest request);

	StartSceneSessionResponse startCustomScene(
			String sceneId,
			StartCustomSceneDialogueRequest request);

	void addMessage(String userId, String sessionId, Message message);

	void endSession(String userId, String sessionId, String stopTime);

	CompleteCustomSceneDialogueResponse completeCustomScene(
			String sceneId,
			String sessionId,
			String stopTime);

	ScenarioDialogueStateResponse advanceCustomSceneState(
			String sceneId,
			String sessionId,
			int turnNo,
			String transcript);

	ScenarioDialogueStateResponse getCustomSceneState(
			String sceneId,
			String sessionId);

	TranslateTextResponse translate(String sessionId, String text);
}
