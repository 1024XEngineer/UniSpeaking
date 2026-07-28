package com.unispeaking.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.vo.prompt.FreeChatPromptContext;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.service.prompt.FreeChatPromptService;
import org.junit.jupiter.api.Test;

class FreeChatPromptServiceTest {

	@Test
	void buildsClaraFreeChatPromptWithLearnerContextAndPreferenceSlots() {
		var service = new FreeChatPromptService();
		var profile = new UserProfile(
				"user-1",
				"INTERMEDIATE",
				"Katerina",
				"zh-CN",
				"Learner likes coffee and wants gentle correction.");
		var sceneConfig = new SceneConfig(
				SceneType.FREE_CHAT,
				ProviderType.QWEN,
				null,
				"Katerina",
				true);

		String prompt = service.build(new FreeChatPromptContext(profile, sceneConfig, "weekend travel")).systemPrompt();

		assertTrue(prompt.contains("Speak as Clara Chen"));
		assertTrue(prompt.contains("Current free-chat topic: weekend travel"));
		assertTrue(prompt.contains("Learner level: INTERMEDIATE"));
		assertTrue(prompt.contains("Learner native language: zh-CN"));
		assertTrue(prompt.contains("Learner likes coffee and wants gentle correction."));
		assertTrue(prompt.contains("Reserved user preference fields:"));
		assertTrue(prompt.contains("Correction preference: not provided yet"));
		assertTrue(prompt.contains("If a field says not provided, do not invent it."));
	}
}
