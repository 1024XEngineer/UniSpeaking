package com.unispeaking.service.session;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.vo.scene.SceneType;

public interface SessionService {

	StartSessionResponse startSession(
			SceneType sceneType,
			String sceneId,
			String prompt);
	void endSession(String userId, String sessionId, String stopTime);

	void addMessage(String userId, String sessionId, Message message);
}
