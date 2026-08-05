package com.unispeaking.component.statemachine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.vo.scene.IeltsPart2Event;
import org.junit.jupiter.api.Test;

class IeltsPart2StateMachineTest {

	private final IeltsPart2StateMachine stateMachine =
			new IeltsPart2StateMachine();

	@Test
	void preparationThenLongTurnThenCompletionIsDeterministic() {
		var preparation = stateMachine.start("ielts_2", "session_2");
		assertEquals("PREPARATION", preparation.phase());
		assertFalse(preparation.completed());

		var longTurn = stateMachine.advance(
				"ielts_2",
				"session_2",
				IeltsPart2Event.PREPARATION_COMPLETE);
		assertEquals("LONG_TURN", longTurn.phase());
		assertTrue(longTurn.controlInstruction().contains("Please begin speaking now."));

		var completed = stateMachine.advance(
				"ielts_2",
				"session_2",
				IeltsPart2Event.LONG_TURN_TIME_LIMIT);
		assertEquals("FINISHED", completed.phase());
		assertTrue(completed.completed());
		assertTrue(completed.controlInstruction().contains("end of Part 2"));
	}

	@Test
	void answerCannotCompleteBeforePreparationEnds() {
		stateMachine.start("ielts_2", "session_2");
		assertThrows(
				BusinessException.class,
				() -> stateMachine.advance(
						"ielts_2",
						"session_2",
						IeltsPart2Event.ANSWER_COMPLETE));
	}

	@Test
	void semanticAnswerCompletionUsesSameClosingState() {
		stateMachine.start("ielts_2", "session_2");
		stateMachine.advance(
				"ielts_2",
				"session_2",
				IeltsPart2Event.PREPARATION_COMPLETE);

		var completed = stateMachine.advance(
				"ielts_2",
				"session_2",
				IeltsPart2Event.ANSWER_COMPLETE);
		assertTrue(completed.completed());
		assertEquals("FINISHED", completed.phase());
	}
}
