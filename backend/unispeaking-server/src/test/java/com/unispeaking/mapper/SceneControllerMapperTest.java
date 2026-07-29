package com.unispeaking.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.unispeaking.domain.dto.request.CustomSceneRequest;
import com.unispeaking.domain.dto.request.StartFreeChatRequest;
import com.unispeaking.domain.vo.scene.SceneType;
import org.junit.jupiter.api.Test;

class SceneControllerMapperTest {

	@Test
	void keepsFreeChatAndCustomSceneControllerInputsSeparated() {
		SceneSessionMapper freeChatMapper = new SceneSessionMapper();
		CustomSceneMapper customSceneMapper = new CustomSceneMapper();

		var freeChat = freeChatMapper.toFreeChatSessionRequest(new StartFreeChatRequest(
				"free-chat-offer",
				null,
				"qwen3.5-omni-flash-realtime",
				"Dolce",
				true));
		var customScene = customSceneMapper.toStartSessionRequest(
				new CustomSceneRequest(
						"user-1",
						"slow",
						"coffee shop",
						"offer",
						null,
						null,
						null,
						null));

		assertEquals(SceneType.FREE_CHAT, freeChat.sceneType());
		assertEquals("free-chat-offer", freeChat.offerSdp());
		assertEquals(null, freeChat.sceneInput());
		assertEquals(null, freeChat.userPreference());
		assertEquals(SceneType.CUSTOM_SCENE, customScene.sceneType());
		assertEquals("offer", customScene.offerSdp());
	}
}
