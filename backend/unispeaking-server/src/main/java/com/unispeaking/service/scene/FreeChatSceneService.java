package com.unispeaking.service.scene;

import com.unispeaking.domain.dto.scene.TranslateTextResponse;
import com.unispeaking.domain.dto.session.StartFreeChatRequest;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;

public interface FreeChatSceneService {

	StartSceneSessionResponse startSession(StartFreeChatRequest request);

	TranslateTextResponse translate(String sessionId, String text);
}
