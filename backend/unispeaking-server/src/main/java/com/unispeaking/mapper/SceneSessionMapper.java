package com.unispeaking.mapper;

import com.unispeaking.domain.dto.request.StartFreeChatRequest;
import com.unispeaking.domain.dto.scene.StartSceneSessionRequest;
import com.unispeaking.domain.vo.scene.SceneType;
import org.springframework.stereotype.Component;

@Component
public class SceneSessionMapper {

	public StartSceneSessionRequest toFreeChatSessionRequest(StartFreeChatRequest request) {
		String sceneInput = request.prompt() == null || request.prompt().isBlank()
				? request.topic()
				: request.prompt();
		return new StartSceneSessionRequest(
				request.userId(),
				SceneType.FREE_CHAT,
				sceneInput,
				request.userPreference(),
				request.offerSdp(),
				request.provider(),
				request.model(),
				request.voice(),
				request.translationEnabled());
	}
}
