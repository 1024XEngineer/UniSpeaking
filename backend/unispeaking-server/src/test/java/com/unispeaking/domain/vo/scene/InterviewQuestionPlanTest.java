package com.unispeaking.domain.vo.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewQuestionPlanTest {

	@Test
	void fixesFiveQuestionsAndDifficultyFollowUpLimit() {
		InterviewQuestionPlan plan = plan(InterviewDifficulty.CHALLENGE);

		assertEquals(5, plan.mainQuestions().size());
		assertEquals(2, plan.maxFollowUps());
		assertThrows(
				UnsupportedOperationException.class,
				() -> plan.mainQuestions().clear());
	}

	@Test
	void rejectsWrongCountSequenceOrDifficultyLimit() {
		List<InterviewPlannedQuestion> questions = new ArrayList<>(List.of(
				new InterviewPlannedQuestion(1, "One", 1)));

		assertThrows(
				IllegalArgumentException.class,
				() -> new InterviewQuestionPlan(InterviewDifficulty.BASIC, questions));

		List<InterviewPlannedQuestion> wrongSequence = questions(5, 1);
		wrongSequence.set(2, new InterviewPlannedQuestion(4, "Four", 1));
		assertThrows(
				IllegalArgumentException.class,
				() -> new InterviewQuestionPlan(InterviewDifficulty.STANDARD, wrongSequence));

		assertThrows(
				IllegalArgumentException.class,
				() -> new InterviewQuestionPlan(
						InterviewDifficulty.BASIC,
						questions(5, 2)));
	}

	private static InterviewQuestionPlan plan(InterviewDifficulty difficulty) {
		return new InterviewQuestionPlan(difficulty, questions(5,
				difficulty == InterviewDifficulty.CHALLENGE ? 2 : 1));
	}

	private static List<InterviewPlannedQuestion> questions(int count, int limit) {
		return java.util.stream.IntStream.rangeClosed(1, count)
				.mapToObj(no -> new InterviewPlannedQuestion(no, "Question " + no, limit))
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
	}
}
