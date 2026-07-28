package com.unispeaking.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.vo.prompt.CustomScenePromptContext;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.prompt.CustomScenePromptService;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomScenePromptServiceTest {

	@Test
	void buildsPromptWithSceneInputPreferenceAndLearningMaterials() {
		var service = new CustomScenePromptService();
		var profile = new UserProfile("user-1", "BASIC", "Katerina", "zh-CN", "Needs slower replies.");
		var sceneConfig = new SceneConfig(SceneType.CUSTOM_SCENE, ProviderType.QWEN, null, "Katerina", true);

		String prompt = service.build(new CustomScenePromptContext(
				profile,
				sceneConfig,
				SceneType.CUSTOM_SCENE,
				"gym membership consultation",
				"likes gentle correction",
				List.of(new LearningContentItem("word_1", "membership", "会员", "")),
				List.of(new LearningContentItem("phrase_1", "Could you tell me more about this situation?", "你能多介绍一下吗？", "")),
				List.of(new LearningContentItem("sentence_1", "What should I do next?", "下一步我该怎么做？", ""))))
				.systemPrompt();

		assertTrue(prompt.contains("gym membership consultation"));
		assertTrue(prompt.contains("likes gentle correction"));
		assertTrue(prompt.contains("membership = 会员"));
		assertTrue(prompt.contains("Could you tell me more about this situation?"));
		assertTrue(prompt.contains("Start directly with the role-play context"));
	}
}
