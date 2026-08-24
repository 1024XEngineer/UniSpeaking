package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.component.scene.CustomSceneGenerator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
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
		assertEquals("住宿", scene.label());
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
		assertTrue(prompt.getValue().contains("餐饮, 购物, 出行, 住宿"));
		assertTrue(prompt.getValue().contains(
				"Never require an optional purchase, facility question"));
		assertTrue(prompt.getValue().contains(
				"either acceptance or refusal resolves"));
		assertTrue(prompt.getValue().contains(
				"must not request teaching feedback"));
		assertTrue(prompt.getValue().contains(
				"reusable lexical chunk or collocation of 2 to 6 English"));
		assertTrue(prompt.getValue().contains(
				"not a complete clause or sentence"));
		assertTrue(prompt.getValue().contains(
				"background must contain only facts observable to both roles"));
		assertTrue(prompt.getValue().contains(
				"Do not place learner-side answers, preferences, budget, or desired choices in background"));
	}

	@Test
	void retriesWhenFirstResponseHasTooFewWords() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		String rejectedResponse = validResponse(3);
		when(registry.executeLlmTask(anyString(), isNull()))
				.thenReturn(rejectedResponse, validResponse(5));
		var service = new CustomSceneGenerator(registry, objectMapper);
		Logger logger = (Logger) LoggerFactory.getLogger(CustomSceneGenerator.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		com.unispeaking.domain.po.scene.CustomSceneDefinition scene;
		try {
			scene = service.generate(
					"custom_retry",
					"user-1",
					"餐厅处理点餐错误",
					null,
					new UserProfile("user-1", "C", "Katerina", "zh-CN", ""));
		}
		finally {
			logger.detachAppender(appender);
		}

		assertEquals(5, scene.wordList().size());
		verify(registry, times(2)).executeLlmTask(anyString(), isNull());
		String logs = appender.list.stream()
				.map(ILoggingEvent::getFormattedMessage)
				.collect(java.util.stream.Collectors.joining("\n"));
		assertTrue(logs.contains("response rejected sceneId=custom_retry attempt=1"));
		assertTrue(logs.contains("llmMs="));
		assertTrue(logs.contains("parseMs="));
		assertTrue(logs.contains("responseChars=" + rejectedResponse.length()));
		assertTrue(!logs.contains(rejectedResponse));
	}

	@Test
	void retriesWhenModelReturnsLabelOutsideAllowList() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTask(anyString(), isNull()))
				.thenReturn(validResponse(5).replace("住宿", "旅游"), validResponse(5));
		var service = new CustomSceneGenerator(registry, objectMapper);

		var scene = service.generate(
				"custom_label_retry",
				"user-1",
				"酒店办理入住",
				null,
				new UserProfile("user-1", "B", "Katerina", "zh-CN", ""));

		assertEquals("住宿", scene.label());
		verify(registry, times(2)).executeLlmTask(anyString(), isNull());
	}

	@Test
	void retriesWhenPhraseListContainsCompleteSentences() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTask(anyString(), isNull()))
				.thenReturn(
						validResponse(5, List.of(
								"There is a hole in it",
								"I would like to return this",
								"Can I get my money back?",
								"It was bought yesterday",
								"Do you have the receipt?")),
						validResponse(5));
		var service = new CustomSceneGenerator(registry, objectMapper);

		var scene = service.generate(
				"custom_phrase_retry",
				"user-1",
				"退货退款",
				null,
				new UserProfile("user-1", "B", "Katerina", "zh-CN", ""));

		assertEquals("check in", scene.phraseList().getFirst().englishText());
		verify(registry, times(2)).executeLlmTask(anyString(), isNull());
	}

	@Test
	void acceptsReusableLexicalChunksAsPhrases() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTask(anyString(), isNull()))
				.thenReturn(validResponse(5, List.of(
						"money back",
						"return this item",
						"proof of purchase",
						"ask for a refund",
						"damaged product")));
		var service = new CustomSceneGenerator(registry, objectMapper);

		var scene = service.generate(
				"custom_phrase_chunks",
				"user-1",
				"退货退款",
				null,
				new UserProfile("user-1", "B", "Katerina", "zh-CN", ""));

		assertEquals("return this item", scene.phraseList().get(1).englishText());
		verify(registry).executeLlmTask(anyString(), isNull());
	}

	@Test
	void retriesWhenPhraseStartsWithNominalSubjectClause() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTask(anyString(), isNull()))
				.thenReturn(
						validResponse(5, List.of(
								"The item is defective",
								"return this item",
								"proof of purchase",
								"ask for a refund",
								"damaged product")),
						validResponse(5));
		var service = new CustomSceneGenerator(registry, objectMapper);

		var scene = service.generate(
				"custom_nominal_clause_retry",
				"user-1",
				"退货退款",
				null,
				new UserProfile("user-1", "B", "Katerina", "zh-CN", ""));

		assertEquals("check in", scene.phraseList().getFirst().englishText());
		verify(registry, times(2)).executeLlmTask(anyString(), isNull());
	}

	@Test
	void acceptsJsonFenceAndNormalizesOptionalInstruction() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTask(anyString(), isNull()))
				.thenReturn("```json\n" + validResponse(5) + "\n```");
		var service = new CustomSceneGenerator(registry, objectMapper);

		var scene = service.generate(
				"custom_fenced", "user-1", "酒店办理入住", null,
				new UserProfile("user-1", "B", "Katerina", "zh-CN", ""));

		assertEquals("保持礼貌，每次回复不超过三句话。", scene.customInstruction());
		verify(registry).executeLlmTask(anyString(), isNull());
	}

	@Test
	void rejectsNullBlankAndOverlongSceneInputBeforeCallingLlm() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		var service = new CustomSceneGenerator(registry, objectMapper);

		for (String input : new String[] {null, " ", "x".repeat(501)}) {
			BusinessException exception = assertThrows(BusinessException.class,
					() -> service.generate("custom_invalid", "user-1", input, null,
							new UserProfile("user-1", "B", "Katerina", "zh-CN", "")));
			assertEquals("INVALID_SCENE_INPUT", exception.code());
		}
		verify(registry, never()).executeLlmTask(anyString(), isNull());
	}

	@Test
	void retriesMalformedJsonDuplicateKeysTrailingTokensAndMarkdown() {
		String base = validResponse(5);
		String[] invalid = {
				"not json",
				base + " trailing",
				"```json\n" + base + "\n``` trailing",
				base.replaceFirst("\\{", "{\"title\":\"duplicate\",")
		};
		for (int index = 0; index < invalid.length; index++) {
			final int caseIndex = index;
			AiProviderRegistry registry = mock(AiProviderRegistry.class);
			when(registry.executeLlmTask(anyString(), isNull()))
					.thenReturn(invalid[index], invalid[index]);
			var service = new CustomSceneGenerator(registry, objectMapper);

			BusinessException exception = assertThrows(BusinessException.class,
					() -> service.generate("custom_json_" + caseIndex, "user-1", "酒店办理入住", null,
							new UserProfile("user-1", "B", "Katerina", "zh-CN", "")));
			assertEquals("CUSTOM_SCENE_LLM_RESPONSE_INVALID", exception.code());
			verify(registry, times(2)).executeLlmTask(anyString(), isNull());
		}
	}

	@Test
	void retriesWhenSuccessFactorHasInvalidBoundsTypesDuplicatesOrMissingFields() {
		String base = validResponse(5);
		String[] invalid = {
				base.replace("\"estimated_minutes\":6", "\"estimated_minutes\":2"),
				base.replace("\"minimum_user_turns\":5", "\"minimum_user_turns\":7"),
				base.replace("\"maximum_user_turns\":10", "\"maximum_user_turns\":9"),
				base.replace("\"completion_rule\":\"ALL_REQUIRED_OUTCOMES\"", "\"completion_rule\":\"ANY\""),
				base.replace("\"required_outcomes\":[\"说明预订姓名\",\"确认房型和入住时间\",\"询问早餐和退房时间\"]",
						"\"required_outcomes\":[\"说明预订姓名\",\"说明预订姓名\",\"询问早餐和退房时间\"]"),
				base.replace("\"closing_instruction\":\"总结房间信息并祝用户入住愉快\"", "\"closing_instruction\":null")
		};
		assertAllRejected(invalid);
	}

	@Test
	void retriesWhenWordsPhrasesOrSentencesViolateCountUniquenessAndReferences() {
		String base = validResponse(5);
		String[] invalid = {
				validResponse(3),
				base.replace("\"word\":\"passport\"", "\"word\":\"reservation\""),
				base.replace("\"phrases\":[", "\"phrases\":[{"),
				base.replace("\"sentence\":\"Could I book a room that is available?\"",
						"\"sentence\":\"This sentence uses unrelated vocabulary.\""),
				base.replace("\"sentences\":[", "\"sentences\":[{"),
				base.replace("\"translation\":\"释义0\"", "\"translation\":\"\"")
		};
		assertAllRejected(invalid);
	}

	@Test
	void rejectsPhraseWordCountPunctuationAndClauseShapes() {
		String base = validResponse(5);
		String[] invalid = {
				base.replace("\"phrase\":\"check in\"", "\"phrase\":\"check\""),
				base.replace("\"phrase\":\"check in\"", "\"phrase\":\"one two three four five six seven\""),
				base.replace("\"phrase\":\"check in\"", "\"phrase\":\"check in.\""),
				base.replace("\"phrase\":\"check in\"", "\"phrase\":\"Can I get help\""),
				base.replace("\"phrase\":\"check in\"", "\"phrase\":\"There is a room\""),
				base.replace("\"phrase\":\"check in\"", "\"phrase\":\"The room is ready\"")
		};
		assertAllRejected(invalid);
	}

	@Test
	void propagatesProviderFailuresAndReturnsLastInvalidResponseAfterTwoAttempts() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTask(anyString(), isNull()))
				.thenThrow(new IllegalStateException("provider unavailable"));
		var service = new CustomSceneGenerator(registry, objectMapper);
		assertThrows(IllegalStateException.class,
				() -> service.generate("custom_provider", "user-1", "酒店办理入住", null,
						new UserProfile("user-1", "B", "Katerina", "zh-CN", "")));
		verify(registry).executeLlmTask(anyString(), isNull());

		AiProviderRegistry invalidRegistry = mock(AiProviderRegistry.class);
		when(invalidRegistry.executeLlmTask(anyString(), isNull()))
				.thenReturn("{}", "{}");
		var invalidService = new CustomSceneGenerator(invalidRegistry, objectMapper);
		BusinessException exception = assertThrows(BusinessException.class,
				() -> invalidService.generate("custom_invalid_final", "user-1", "酒店办理入住", null,
						new UserProfile("user-1", "B", "Katerina", "zh-CN", "")));
		assertEquals("CUSTOM_SCENE_LLM_RESPONSE_INVALID", exception.code());
		verify(invalidRegistry, times(2)).executeLlmTask(anyString(), isNull());
	}

	private void assertAllRejected(String[] responses) {
		for (int index = 0; index < responses.length; index++) {
			final int caseIndex = index;
			AiProviderRegistry registry = mock(AiProviderRegistry.class);
			when(registry.executeLlmTask(anyString(), isNull()))
					.thenReturn(responses[index], responses[index]);
			var service = new CustomSceneGenerator(registry, objectMapper);
			BusinessException exception = assertThrows(BusinessException.class,
					() -> service.generate("custom_rejected_" + caseIndex, "user-1", "酒店办理入住", null,
							new UserProfile("user-1", "B", "Katerina", "zh-CN", "")));
			assertEquals("CUSTOM_SCENE_LLM_RESPONSE_INVALID", exception.code());
			verify(registry, times(2)).executeLlmTask(anyString(), isNull());
		}
	}

	private String validResponse(int wordCount) {
		return validResponse(wordCount, List.of(
				"check in",
				"single room",
				"book a room",
				"show my passport",
				"confirm the reservation"));
	}

	private String validResponse(int wordCount, List<String> phrases) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("title", "酒店办理入住");
		root.put("label", "住宿");
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
		List<Map<String, String>> phraseItems = new ArrayList<>();
		for (int index = 0; index < phrases.size(); index++) {
			phraseItems.add(item("phrase", phrases.get(index), "短语" + index));
		}
		root.put("phrases", phraseItems);
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
