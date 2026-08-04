package com.unispeaking.component.statemachine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.statemachine.ScenarioSuccessFactorParser;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.dto.session.ScenarioDialogueStateResponse;
import com.unispeaking.domain.po.scene.ScenarioDialogueEvent;
import com.unispeaking.domain.po.scene.ScenarioSuccessFactor;
import com.unispeaking.domain.vo.scene.ScenarioDialogueCompletionReason;
import com.unispeaking.domain.vo.scene.ScenarioDialogueEventType;
import com.unispeaking.domain.vo.scene.ScenarioDialogueStage;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioDialogueStateMachineTest {

	private ScenarioDialogueEventExtractor eventExtractor;
	private SessionMessageRepository messageRepository;
	private ScenarioDialogueStateMachine stateMachine;

	@BeforeEach
	void setUp() {
		ScenarioSuccessFactorParser successFactorParser =
				mock(ScenarioSuccessFactorParser.class);
		eventExtractor = mock(ScenarioDialogueEventExtractor.class);
		messageRepository = mock(SessionMessageRepository.class);
		when(successFactorParser.parse(anyString(), anyString()))
				.thenReturn(new ScenarioSuccessFactor(
						1,
						4,
						Map.of(
								"drink", "choose a drink",
								"payment", "choose payment"),
						"complete the order",
						"Thank the learner."));
		when(messageRepository.findMessages(anyString()))
				.thenReturn(List.of(new Message(0, "What would you like?", null)));
		stateMachine = new ScenarioDialogueStateMachine(
				successFactorParser,
				eventExtractor,
				messageRepository);
	}

	@Test
	void advancesFromGreetingThroughConfirmationAndClosing() {
		var started = start("session_1");
		assertEquals(ScenarioDialogueStage.GREETING, started.stage());
		assertTrue(started.controlInstruction().contains("choose a drink"));
		assertFalse(started.completed());

		when(eventExtractor.extract(any(), anyString(), anyList()))
				.thenReturn(
						outcome("drink", "latte"),
						outcome("payment", "card"),
						new ScenarioDialogueEvent(
								ScenarioDialogueEventType.USER_CONFIRMED,
								Map.of(),
								0.99));

		var collecting = stateMachine.advance("session_1", 1, "A latte.");
		assertEquals(ScenarioDialogueStage.COLLECTING_INFORMATION, collecting.stage());
		assertTrue(collecting.outcomes().stream()
				.anyMatch(outcome -> outcome.outcomeId().equals("drink")
						&& outcome.satisfied()));

		var duplicate = stateMachine.advance("session_1", 1, "Use cash.");
		assertEquals(1, duplicate.effectiveUserTurns());
		verify(eventExtractor, times(1)).extract(any(), anyString(), anyList());

		var confirmation = stateMachine.advance("session_1", 2, "By card.");
		assertEquals(ScenarioDialogueStage.CONFIRMATION, confirmation.stage());
		assertTrue(confirmation.controlInstruction().contains("final confirmation"));

		var completed = stateMachine.advance("session_1", 3, "That is correct.");
		assertTrue(completed.completed());
		assertEquals(ScenarioDialogueCompletionReason.GOAL_ACHIEVED,
				completed.completionReason());
		assertTrue(completed.controlInstruction().contains("Thank the learner."));

		var closing = stateMachine.beginClosing("session_1");
		assertEquals(ScenarioDialogueStage.CLOSING, closing.stage());
		assertTrue(closing.controlInstruction().isEmpty());
		assertEquals(3,
				stateMachine.advance("session_1", 4, "ignored")
						.effectiveUserTurns());
	}

	@Test
	void recordsExtractorFailureAndValidatesInput() {
		start("session_2");
		when(eventExtractor.extract(any(), anyString(), anyList()))
				.thenThrow(new IllegalStateException("provider unavailable"));

		var response = stateMachine.advance("session_2", 1, "  Continue  ");

		assertEquals(1, response.effectiveUserTurns());
		assertTrue(response.warning().contains("语义目标提取失败"));
		assertThrows(BusinessException.class,
				() -> stateMachine.advance("session_2", 0, "invalid"));
		assertThrows(BusinessException.class,
				() -> stateMachine.advance("session_2", 2, " "));
	}

	@Test
	void removesStateAndRejectsUnknownSession() {
		start("session_3");
		assertTrue(stateMachine.findState("session_3").isPresent());

		stateMachine.remove("session_3");

		assertTrue(stateMachine.findState("session_3").isEmpty());
		BusinessException exception = assertThrows(
				BusinessException.class,
				() -> stateMachine.getState("session_3"));
		assertEquals("SCENARIO_STATE_NOT_FOUND", exception.code());
	}

	private ScenarioDialogueStateResponse start(
			String sessionId) {
		return stateMachine.start(
				sessionId,
				"custom_1",
				"{}",
				"Order coffee");
	}

	private ScenarioDialogueEvent outcome(String key, String value) {
		return new ScenarioDialogueEvent(
				ScenarioDialogueEventType.OUTCOME_UPDATE,
				Map.of(key, value),
				0.95);
	}
}
