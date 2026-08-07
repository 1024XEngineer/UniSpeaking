package com.unispeaking.common.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.vo.scene.SceneType;
import org.junit.jupiter.api.Test;

class IdGeneratorTest {

	@Test
	void sceneIdsUseTheCurrentSceneTypePrefix() {
		assertTrue(SceneIdGenerator.generate(SceneType.FREE_CHAT)
				.startsWith("freechat_"));
		assertTrue(SceneIdGenerator.generate(SceneType.CUSTOM_SCENE)
				.startsWith("custom_"));
		assertTrue(SceneIdGenerator.generate(SceneType.CUSTOM_SCENE)
				.startsWith("custom_"));
		assertTrue(SceneIdGenerator.generate(SceneType.IELTS_SCENE)
				.startsWith("ielts_"));
	}

	@Test
	void sessionIdsKeepSceneTypeAndSessionMeaning() {
		assertTrue(SessionIdGenerator.generate(SceneType.FREE_CHAT)
				.startsWith("freechat_session_"));
		assertTrue(SessionIdGenerator.generate(SceneType.CUSTOM_SCENE)
				.startsWith("custom_session_"));
	}
}
