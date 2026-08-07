package com.unispeaking.component.statemachine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.vo.scene.IeltsContentQuestion;
import com.unispeaking.domain.vo.scene.IeltsPart;
import java.util.List;
import org.junit.jupiter.api.Test;

class IeltsQuestionStateMachineTest {

	private final IeltsQuestionStateMachine stateMachine =
			new IeltsQuestionStateMachine();

	@Test
	void partOneUsesIntroductionThenEveryPreparedQuestionExactlyOnce() {
		stateMachine.start("ielts_1", "session_1", IeltsPart.PART_1,
				questions("Question one?", "Question two?"));

		var firstQuestion = stateMachine.advance("ielts_1", "session_1", 1);
		assertTrue(firstQuestion.openingCompleted());
		assertEquals(0, firstQuestion.answeredQuestions());
		assertTrue(firstQuestion.controlInstruction().contains("Question one?"));
		assertTrue(firstQuestion.controlInstruction().contains("ask a follow-up"));

		var secondQuestion = stateMachine.advance("ielts_1", "session_1", 2);
		assertEquals(1, secondQuestion.answeredQuestions());
		assertTrue(secondQuestion.controlInstruction().contains("Question two?"));

		var completed = stateMachine.advance("ielts_1", "session_1", 3);
		assertTrue(completed.completed());
		assertTrue(completed.controlInstruction().contains("end of Part 1"));
	}

	@Test
	void partThreeClosesImmediatelyAfterFinalPreparedAnswer() {
		stateMachine.start("ielts_3", "session_3", IeltsPart.PART_3,
				questions("Why is that?"));

		var completed = stateMachine.advance("ielts_3", "session_3", 1);
		assertTrue(completed.completed());
		assertFalse(completed.controlInstruction().contains("Why is that?"));
		assertTrue(completed.controlInstruction().contains("end of the speaking test"));
	}

	@Test
	void duplicateTurnDoesNotAdvanceTwice() {
		stateMachine.start("ielts_3", "session_3", IeltsPart.PART_3,
				questions("One?", "Two?"));

		stateMachine.advance("ielts_3", "session_3", 1);
		var duplicate = stateMachine.advance("ielts_3", "session_3", 1);
		assertEquals(1, duplicate.answeredQuestions());
		assertTrue(duplicate.controlInstruction().contains("Two?"));
	}

	@Test
	void partThreeTimeLimitAddsOneShortTransitionBeforeNextQuestion() {
		stateMachine.start("ielts_3", "session_3", IeltsPart.PART_3,
				questions("One?", "Two?"));

		var next = stateMachine.advance(
				"ielts_3",
				"session_3",
				1,
				true);

		assertEquals(1, next.answeredQuestions());
		assertTrue(next.controlInstruction().contains(
				"Let's move on to the next question. Two?"));
	}

	private List<IeltsContentQuestion> questions(String... values) {
		return java.util.Arrays.stream(values)
				.map(value -> new IeltsContentQuestion(
						value,
						List.of(),
						List.of()))
				.toList();
	}
}
