package com.unispeaking.common.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import java.util.List;
import org.junit.jupiter.api.Test;

class FiveLayerPromptBuilderTest {

	@Test
	void composesFreeChatPromptFromProfileAndCurrentInput() {
		var service = new FiveLayerPromptBuilder("");
		var profile = new UserProfile(
				"user-1",
				"C",
				"Harvey",
				"FASTER",
				"zh-CN",
				"兴趣与背景：喜欢咖啡和周末旅行。");
		var sceneConfig = new SceneConfig(
				SceneType.FREE_CHAT,
				ProviderType.QWEN,
				null,
				"Katerina",
				true);

		List<String> layers = service.compose(
				profile,
				sceneConfig,
				SceneType.FREE_CHAT,
				"weekend travel",
				"Keep this call focused on planning a short trip.",
				List.of(),
				List.of(),
				List.of());
		String prompt = String.join("\n\n", layers);

		assertEquals(5, layers.size());
		assertTrue(prompt.contains("L1 Base Duty"));
		assertTrue(prompt.contains("You are James"));
		assertTrue(prompt.contains("The learner can express connected ideas."));
		assertTrue(prompt.contains("Speaking speed: 2.0."));
		assertTrue(prompt.contains("兴趣与背景：喜欢咖啡和周末旅行。"));
			assertTrue(prompt.contains("Open conversation mode."));
			assertTrue(prompt.contains("Do not wait silently for the learner to speak."));
			assertTrue(prompt.contains("weekend travel"));
		assertTrue(prompt.contains("Keep this call focused on planning a short trip."));
	}

	@Test
	void composesCustomScenePromptWithLearningMaterials() {
		var service = new FiveLayerPromptBuilder("");
		var profile = new UserProfile(
				"user-1",
				"B",
				"Katerina",
				"MODERATE",
				"zh-CN",
				"兴趣与背景：经常出差，熟悉商务会议。");
		var sceneConfig = new SceneConfig(
				SceneType.CUSTOM_SCENE,
				ProviderType.QWEN,
				null,
				"Katerina",
				true);

		List<String> layers = service.compose(
				profile,
				sceneConfig,
				SceneType.CUSTOM_SCENE,
				"gym membership consultation",
				"likes gentle correction",
				List.of(new LearningContentItem("word_1", "membership", "会员", "")),
				List.of(new LearningContentItem(
						"phrase_1",
						"Could you tell me more about this situation?",
						"你能多介绍一下吗？",
						"")),
				List.of(new LearningContentItem(
						"sentence_1",
						"What should I do next?",
						"下一步我该怎么做？",
						"")));
		String prompt = String.join("\n\n", layers);

		assertEquals(5, layers.size());
		assertTrue(prompt.contains("gym membership consultation"));
		assertTrue(prompt.contains("likes gentle correction"));
		assertTrue(prompt.contains("You are Clara"));
		assertTrue(prompt.contains("The learner can handle basic communication."));
		assertTrue(prompt.contains("Speaking speed: 1.0."));
		assertTrue(prompt.contains("兴趣与背景：经常出差，熟悉商务会议。"));
		assertTrue(prompt.contains("membership = 会员"));
		assertTrue(prompt.contains("Could you tell me more about this situation?"));
		assertTrue(prompt.contains("This scenario hard contract is mandatory"));
	}
}
