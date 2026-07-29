package com.unispeaking.mapper;

import com.unispeaking.domain.dto.request.StartFreeChatRequest;
import com.unispeaking.domain.dto.scene.StartSceneSessionRequest;
import com.unispeaking.domain.vo.scene.SceneType;
import org.springframework.stereotype.Component;

@Component
public class SceneSessionMapper {

	public StartSceneSessionRequest toFreeChatSessionRequest(StartFreeChatRequest request) {
		return new StartSceneSessionRequest(
				null,
				SceneType.FREE_CHAT,
				null,
				null,
				request.offerSdp(),
				request.provider(),
				request.model(),
				request.voice(),
				request.translationEnabled());
	}
}
