package com.unispeaking.component.statemachine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.scene.ScenarioDialogueState;
import com.unispeaking.domain.po.scene.ScenarioSuccessFactor;
import com.unispeaking.domain.vo.scene.ScenarioDialogueEventType;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.component.statemachine.ScenarioDialogueEventExtractor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ScenarioDialogueEventExtractorTest {

	@Test
	void exposesStopConditionAndParsesSemanticCompletion() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTask(anyString(), isNull()))
				.thenReturn("""
						{
						  "type": "GOAL_COMPLETED",
						  "outcome_values": {
						    "outcome_1": "paid in cash"
						  },
						  "confidence": 0.96
						}
						""");
		ScenarioDialogueEventExtractor extractor =
				new ScenarioDialogueEventExtractor(
						registry,
						new ObjectMapper());
		ScenarioDialogueState state = new ScenarioDialogueState(
				"scene_session_1",
				"custom_1",
				new ScenarioSuccessFactor(
						3,
						10,
						Map.of(
								"outcome_1", "select payment",
								"outcome_2", "provide an optional cup name"),
						"The transaction is logically complete.",
						"Close naturally."));

		var event = extractor.extract(
				state,
				"Thank you.",
				List.of(
						new Message(0, "Your order is complete.", null),
						new Message(1, "Thank you.", null)));

		assertEquals(ScenarioDialogueEventType.GOAL_COMPLETED, event.type());
		assertEquals("paid in cash", event.outcomeValues().get("outcome_1"));

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(registry).executeLlmTask(prompt.capture(), isNull());
		assertTrue(prompt.getValue().contains(
				"\"stop_when\":\"The transaction is logically complete.\""));
		assertTrue(prompt.getValue().contains("GOAL_COMPLETED"));
		assertTrue(prompt.getValue().contains(
				"explicitly declining it resolves that outcome"));
		assertTrue(prompt.getValue().contains(
				"changed request is not a"));
	}

	@Test
	void unwrapsFenceFiltersOutcomeValuesAndDefaultsUnknownType() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTask(anyString(), isNull())).thenReturn("""
				```json
				{"type":"not-real","outcome_values":{"outcome_1":"  evidence  ",
				"outcome_2":" ","unknown":"ignored"}}
				```
				""");
		ScenarioDialogueEventExtractor extractor = new ScenarioDialogueEventExtractor(
				registry, new ObjectMapper());

		var event = extractor.extract(state(), "answer", null);

		assertEquals(ScenarioDialogueEventType.NONE, event.type());
		assertEquals(Map.of("outcome_1", "evidence"), event.outcomeValues());
		assertEquals(0.5, event.confidence());
	}

	@Test
	void limitsRecentDialogueToEightAndAcceptsNonObjectOutcomeValues() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTask(anyString(), isNull()))
				.thenReturn("{\"type\":\"NONE\",\"outcome_values\":[]}");
		ScenarioDialogueEventExtractor extractor = new ScenarioDialogueEventExtractor(
				registry, new ObjectMapper());
		List<Message> messages = java.util.stream.IntStream.range(0, 10)
				.mapToObj(index -> new Message(1, "message-" + index, null)).toList();

		extractor.extract(state(), "answer", messages);

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(registry).executeLlmTask(prompt.capture(), isNull());
		assertTrue(!prompt.getValue().contains("message-0"));
		assertTrue(prompt.getValue().contains("message-9"));
	}

	@Test
	void wrapsNullAndMalformedProviderResponses() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		ScenarioDialogueEventExtractor extractor = new ScenarioDialogueEventExtractor(
				registry, new ObjectMapper());
		when(registry.executeLlmTask(anyString(), isNull())).thenReturn(null, "not-json");

		assertEquals(ScenarioDialogueEventType.NONE,
				extractor.extract(state(), "answer", List.of()).type());
		assertThrows(BusinessException.class,
				() -> extractor.extract(state(), "answer", List.of()));
	}

	private ScenarioDialogueState state() {
		return new ScenarioDialogueState(
				"session", "scene",
				new ScenarioSuccessFactor(1, 10,
						Map.of("outcome_1", "one", "outcome_2", "two"),
						"done", "close"));
	}
}
