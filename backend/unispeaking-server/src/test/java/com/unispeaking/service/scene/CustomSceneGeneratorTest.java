package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.component.scene.CustomSceneGenerator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CustomSceneGeneratorTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void generatesCompactLearningContentAndMachineReadableSuccessFactor() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTask(anyString(), isNull()))
				.thenReturn(validResponse(5));
		var service = new CustomSceneGenerator(registry, objectMapper);
		UserProfile profile = new UserProfile(
				"user-1",
				"B",
				"Katerina",
				"MODERATE",
				"zh-CN",
				"喜欢旅行",
				"{\"learning_goal\":\"travel\"}");

		var scene = service.generate(
				"custom_abc123",
				"user-1",
				"酒店办理入住",
				"希望练习礼貌表达",
				profile);

		assertEquals(5, scene.wordList().size());
		assertEquals(5, scene.phraseList().size());
		assertEquals(3, scene.sentenceList().size());
		assertTrue(scene.wordList().stream()
				.allMatch(item -> item.contentId().startsWith("word_")));
		assertTrue(scene.phraseList().stream()
				.allMatch(item -> item.contentId().startsWith("phrase_")));
		assertTrue(scene.sentenceList().stream()
				.allMatch(item -> item.contentId().startsWith("sentence_")));
		JsonNode successFactor = objectMapper.readTree(scene.successFactorJson());
		assertEquals(6, successFactor.path("estimated_minutes").intValue());
		assertEquals(5, successFactor.path("minimum_user_turns").intValue());
		assertEquals(10, successFactor.path("maximum_user_turns").intValue());
		assertEquals(
				"ALL_REQUIRED_OUTCOMES",
				successFactor.path("completion_rule").asString());

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(registry).executeLlmTask(prompt.capture(), isNull());
		assertTrue(prompt.getValue().contains("酒店办理入住"));
		assertTrue(prompt.getValue().contains("MODERATE"));
		assertTrue(prompt.getValue().contains("learning_goal"));
	}

	@Test
	void retriesWhenFirstResponseHasTooFewWords() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTask(anyString(), isNull()))
				.thenReturn(validResponse(3), validResponse(5));
		var service = new CustomSceneGenerator(registry, objectMapper);

		var scene = service.generate(
				"custom_retry",
				"user-1",
				"餐厅处理点餐错误",
				null,
				new UserProfile("user-1", "C", "Katerina", "zh-CN", ""));

		assertEquals(5, scene.wordList().size());
		verify(registry, times(2)).executeLlmTask(anyString(), isNull());
	}

	private String validResponse(int wordCount) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("title", "酒店办理入住");
		root.put("background", "用户抵达酒店前台并办理入住。");
		root.put("ai_role", "酒店前台接待员");
		root.put("user_role", "持有预订的住客");
		root.put("learning_goal", "确认预订、提供证件并获取房间信息");
		root.put("custom_instruction", "保持礼貌，每次回复不超过三句话。");
		root.put("success_factor", Map.of(
				"estimated_minutes", 6,
				"minimum_user_turns", 5,
				"maximum_user_turns", 10,
				"required_outcomes", List.of(
						"说明预订姓名",
						"确认房型和入住时间",
						"询问早餐和退房时间"),
				"completion_rule", "ALL_REQUIRED_OUTCOMES",
				"stop_when", "达到最少轮次且三个目标都有明确回答",
				"closing_instruction", "总结房间信息并祝用户入住愉快"));

		List<Map<String, String>> words = new ArrayList<>();
		List<String> availableWords = List.of(
				"reservation",
				"passport",
				"available",
				"confirm",
				"deposit");
		for (int index = 0; index < wordCount; index++) {
			String word = availableWords.get(index);
			words.add(Map.of(
					"word", word,
					"phonetic", "/" + word + "/",
					"translation", "释义" + index));
		}
		root.put("words", words);
		root.put("phrases", List.of(
				item("phrase", "check in", "办理入住"),
				item("phrase", "single room", "单人间"),
				item("phrase", "book a room", "预订房间"),
				item("phrase", "show my passport", "出示护照"),
				item("phrase", "confirm the reservation", "确认预订")));
		root.put("sentences", List.of(
				Map.of(
						"sentence",
						"I would like to check in and confirm the reservation.",
						"translation",
						"我想办理入住并确认预订。"),
				Map.of(
						"sentence",
						"Could I book a room that is available?",
						"translation",
						"我可以预订一间空房吗？"),
				Map.of(
						"sentence",
						"Here is my passport for the reservation.",
						"translation",
						"这是我用于预订的护照。")));
		return objectMapper.writeValueAsString(root);
	}

	private Map<String, String> item(
			String field,
			String value,
			String translation) {
		return Map.of(
				field,
				value,
				"phonetic",
				"/" + value + "/",
				"translation",
				translation);
	}
}
