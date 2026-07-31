package com.unispeaking.service.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.scene.ScenarioDialogueState;
import com.unispeaking.domain.po.scene.ScenarioSuccessFactor;
import com.unispeaking.domain.vo.scene.ScenarioDialogueEventType;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.scene.impl.ScenarioDialogueEventExtractor;
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
	}
}
