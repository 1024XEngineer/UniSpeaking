package com.unispeaking.mapper;

import com.unispeaking.domain.dto.request.CustomSceneRequest;
import com.unispeaking.domain.dto.scene.SceneGenerationRequest;
import com.unispeaking.domain.dto.scene.StartSceneSessionRequest;
import com.unispeaking.domain.vo.scene.SceneType;
import org.springframework.stereotype.Component;

@Component
public class CustomSceneMapper {

	public SceneGenerationRequest toGenerationRequest(CustomSceneRequest request) {
		return new SceneGenerationRequest(
				request.userId(),
				request.userPreference(),
				SceneType.CUSTOM_SCENE,
				request.sceneInput());
	}

	public StartSceneSessionRequest toStartSessionRequest(CustomSceneRequest request) {
		return new StartSceneSessionRequest(
				request.userId(),
				SceneType.CUSTOM_SCENE,
				request.sceneInput(),
				request.userPreference(),
				request.offerSdp(),
				request.provider(),
				request.model(),
				request.voice(),
				request.translationEnabled());
	}
}
