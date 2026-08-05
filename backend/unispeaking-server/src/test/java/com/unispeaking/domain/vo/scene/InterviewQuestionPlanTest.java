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
		assertEquals(
				InterviewFollowUpFocus.TRADE_OFF_REFLECTION_COMPLEX_SCENARIO,
				plan.followUpFocus());
		assertThrows(
				UnsupportedOperationException.class,
				() -> plan.mainQuestions().clear());
	}

	@Test
	void createsFiveQuestionPlanFromProviderIndependentTextsForEveryDifficulty() {
		InterviewQuestionPlan basic = generated(InterviewDifficulty.BASIC);
		InterviewQuestionPlan standard = generated(InterviewDifficulty.STANDARD);
		InterviewQuestionPlan challenge = generated(InterviewDifficulty.CHALLENGE);

		assertEquals(InterviewFollowUpFocus.CLARIFICATION, basic.followUpFocus());
		assertEquals(1, basic.mainQuestion(5).maxFollowUps());
		assertEquals(
				InterviewFollowUpFocus.REASON_ACTION_RESULT,
				standard.followUpFocus());
		assertEquals(1, standard.mainQuestion(5).maxFollowUps());
		assertEquals(
				InterviewFollowUpFocus.TRADE_OFF_REFLECTION_COMPLEX_SCENARIO,
				challenge.followUpFocus());
		assertEquals(2, challenge.mainQuestion(5).maxFollowUps());
	}

	@Test
	void rejectsGeneratedQuestionListsThatAreNotExactlyFiveNonBlankTexts() {
		assertThrows(
				IllegalArgumentException.class,
				() -> InterviewQuestionPlan.fromGeneratedMainQuestions(
						InterviewDifficulty.BASIC,
						List.of("Q1")));
		assertThrows(
				IllegalArgumentException.class,
				() -> InterviewQuestionPlan.fromGeneratedMainQuestions(
						InterviewDifficulty.STANDARD,
						List.of("Q1", "Q2", " ", "Q4", "Q5")));
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

	private static InterviewQuestionPlan generated(InterviewDifficulty difficulty) {
		return InterviewQuestionPlan.fromGeneratedMainQuestions(
				difficulty,
				List.of("Q1", "Q2", "Q3", "Q4", "Q5"));
	}

	private static List<InterviewPlannedQuestion> questions(int count, int limit) {
		return java.util.stream.IntStream.rangeClosed(1, count)
				.mapToObj(no -> new InterviewPlannedQuestion(no, "Question " + no, limit))
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
	}
}
