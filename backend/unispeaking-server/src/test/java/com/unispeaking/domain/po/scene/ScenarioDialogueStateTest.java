package com.unispeaking.domain.po.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.unispeaking.domain.vo.scene.ScenarioDialogueCompletionReason;
import com.unispeaking.domain.vo.scene.ScenarioDialogueEventType;
import com.unispeaking.domain.vo.scene.ScenarioDialogueStage;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScenarioDialogueStateTest {

	@Test
	void allOutcomesRequireFinalConfirmation() {
		ScenarioDialogueState state = state(3, 10);
		state.apply(1, update("outcome_1", "latte"));
		state.apply(2, update("outcome_2", "card"));
		state.apply(3, update("outcome_3", "take away"));

		assertEquals(ScenarioDialogueStage.CONFIRMATION, state.getStage());
		assertNull(state.getCompletionReason());

		state.apply(4, new ScenarioDialogueEvent(
				ScenarioDialogueEventType.USER_CONFIRMED,
				Map.of(),
				0.99));

		assertEquals(ScenarioDialogueStage.COMPLETED, state.getStage());
		assertEquals(
				ScenarioDialogueCompletionReason.GOAL_ACHIEVED,
				state.getCompletionReason());
	}

	@Test
	void tenthEffectiveTurnAlwaysCompletes() {
		ScenarioDialogueState state = state(3, 12);
		for (int turn = 1; turn <= 10; turn++) {
			state.apply(turn, ScenarioDialogueEvent.none());
		}

		assertEquals(10, state.getEffectiveUserTurns());
		assertEquals(ScenarioDialogueStage.COMPLETED, state.getStage());
		assertEquals(
				ScenarioDialogueCompletionReason.MAX_TURNS_REACHED,
				state.getCompletionReason());
	}

	@Test
	void duplicateTurnDoesNotIncrementEffectiveTurnCount() {
		ScenarioDialogueState state = state(3, 10);
		state.apply(1, update("outcome_1", "latte"));
		state.apply(1, update("outcome_2", "card"));

		assertEquals(1, state.getEffectiveUserTurns());
		assertEquals(Map.of("outcome_1", "latte"), state.getSatisfiedOutcomes());
	}

	@Test
	void semanticStopConditionCompletesWithoutEveryOptionalOutcome() {
		ScenarioDialogueState state = state(3, 10);
		state.apply(1, update("outcome_1", "large iced Americano"));
		state.apply(2, update("outcome_2", "cash"));
		state.apply(3, new ScenarioDialogueEvent(
				ScenarioDialogueEventType.GOAL_COMPLETED,
				Map.of(),
				0.95));

		assertEquals(ScenarioDialogueStage.COMPLETED, state.getStage());
		assertEquals(
				ScenarioDialogueCompletionReason.GOAL_ACHIEVED,
				state.getCompletionReason());
	}

	@Test
	void semanticStopConditionHonorsMinimumTurnsAndConfidence() {
		ScenarioDialogueState state = state(3, 10);
		state.apply(1, new ScenarioDialogueEvent(
				ScenarioDialogueEventType.GOAL_COMPLETED,
				Map.of(),
				0.95));
		state.apply(2, new ScenarioDialogueEvent(
				ScenarioDialogueEventType.GOAL_COMPLETED,
				Map.of(),
				0.70));

		assertEquals(
				ScenarioDialogueStage.COLLECTING_INFORMATION,
				state.getStage());
		assertNull(state.getCompletionReason());
	}

	@Test
	void completedDialogueMovesToClosingAfterFinalAiResponse() {
		ScenarioDialogueState state = state(3, 10);
		state.apply(1, update("outcome_1", "latte"));
		state.apply(2, update("outcome_2", "card"));
		state.apply(3, update("outcome_3", "take away"));
		state.apply(4, new ScenarioDialogueEvent(
				ScenarioDialogueEventType.USER_CONFIRMED,
				Map.of(),
				0.99));

		state.beginClosing();
		state.apply(5, ScenarioDialogueEvent.none());

		assertEquals(ScenarioDialogueStage.CLOSING, state.getStage());
		assertEquals(4, state.getEffectiveUserTurns());
		assertEquals(
				ScenarioDialogueCompletionReason.GOAL_ACHIEVED,
				state.getCompletionReason());
	}

	private ScenarioDialogueState state(int minimum, int maximum) {
		return new ScenarioDialogueState(
				"session_1",
				"custom_1",
				new ScenarioSuccessFactor(
						minimum,
						maximum,
						Map.of(
								"outcome_1", "choose an item",
								"outcome_2", "choose payment",
								"outcome_3", "choose fulfillment"),
						"complete all outcomes",
						"close naturally"));
	}

	private ScenarioDialogueEvent update(String key, String value) {
		return new ScenarioDialogueEvent(
				ScenarioDialogueEventType.OUTCOME_UPDATE,
				Map.of(key, value),
				0.95);
	}
}
