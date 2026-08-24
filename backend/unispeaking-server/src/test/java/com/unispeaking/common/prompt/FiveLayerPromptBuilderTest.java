package com.unispeaking.common.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
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
	void composesCustomScenePromptWithoutExposingLearningAnswersToTheActor() {
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
		assertFalse(prompt.contains("membership = 会员"));
		assertFalse(prompt.contains("Could you tell me more about this situation?"));
		assertFalse(prompt.contains("What should I do next?"));
		assertTrue(prompt.contains("This scenario hard contract is mandatory"));
	}

	@Test
	void keepsLearnerReferenceAnswersOutOfTheRolePlayActorPrompt() {
		var service = new FiveLayerPromptBuilder("");
		var profile = new UserProfile(
				"user-1",
				"B",
				"Katerina",
				"MODERATE",
				"zh-CN",
				"");
		var sceneConfig = new SceneConfig(
				SceneType.CUSTOM_SCENE,
				ProviderType.QWEN,
				null,
				"Katerina",
				true);
		var sentences = List.of(
				new LearningContentItem(
						"sentence_1",
						"I am looking for a gift for my friend.",
						"我正在为朋友寻找礼物。",
						""),
				new LearningContentItem(
						"sentence_2",
						"She likes reading and tea.",
						"她喜欢阅读和茶。",
						""));
		var definition = new CustomSceneDefinition(
				"custom_gift",
				"user-1",
				"挑选生日礼物",
				"购物",
				"The learner visits a gift shop for a friend who loves reading and tea.",
				"Friendly shop assistant",
				"Customer looking for a gift",
				"Identify a suitable gift and confirm the purchase.",
				"Keep the interaction natural.",
				"{}",
				List.of(),
				List.of(),
				sentences);

		String prompt = String.join("\n\n", service.compose(
				profile,
				sceneConfig,
				SceneType.CUSTOM_SCENE,
				"挑选生日礼物",
				"",
				List.of(),
				List.of(),
				sentences,
				definition));

		assertTrue(prompt.contains("You are only Friendly shop assistant"));
		assertTrue(prompt.contains(
				"In a role-play, the L5 AI role is your only conversational identity"));
		assertTrue(prompt.contains(
				"Never speak, decide, make requests, express needs, or take actions for Customer looking for a gift"));
		assertTrue(prompt.contains("On the first turn"));
		assertTrue(prompt.contains("Do not reveal, hint at, confirm, or offer learner-specific facts"));
		assertFalse(prompt.contains("I am looking for a gift for my friend."));
		assertFalse(prompt.contains("She likes reading and tea."));
	}
}
