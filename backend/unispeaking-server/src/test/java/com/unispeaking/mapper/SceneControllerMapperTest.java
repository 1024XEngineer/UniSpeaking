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
				"user-1",
				SceneType.CUSTOM_SCENE,
				"travel",
				"slow",
				null,
				null,
				null,
				null,
				null,
				null));
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
		assertEquals(SceneType.CUSTOM_SCENE, customScene.sceneType());
		assertEquals("offer", customScene.offerSdp());
	}
}
