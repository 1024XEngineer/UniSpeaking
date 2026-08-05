package com.unispeaking.domain.vo.scene;

import java.util.List;
import java.util.Objects;

public final class InterviewQuestionPlan {

	private static final int MAIN_QUESTION_COUNT = 5;

	private final InterviewDifficulty difficulty;
	private final List<InterviewPlannedQuestion> mainQuestions;

	public InterviewQuestionPlan(
			InterviewDifficulty difficulty,
			List<InterviewPlannedQuestion> mainQuestions) {
		this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
		if (mainQuestions == null || mainQuestions.size() != MAIN_QUESTION_COUNT) {
			throw new IllegalArgumentException("Interview must contain exactly five main questions");
		}
		List<InterviewPlannedQuestion> copied = List.copyOf(mainQuestions);
		for (int index = 0; index < copied.size(); index++) {
			InterviewPlannedQuestion question = copied.get(index);
			if (question.questionNo() != index + 1) {
				throw new IllegalArgumentException("main question numbers must be consecutive");
			}
			int expectedLimit = expectedFollowUpLimit(difficulty);
			if (question.maxFollowUps() != expectedLimit) {
				throw new IllegalArgumentException("follow-up limit does not match difficulty");
			}
		}
		this.mainQuestions = copied;
	}

	public InterviewDifficulty difficulty() {
		return difficulty;
	}

	public List<InterviewPlannedQuestion> mainQuestions() {
		return mainQuestions;
	}

	public InterviewPlannedQuestion mainQuestion(int questionNo) {
		if (questionNo < 1 || questionNo > MAIN_QUESTION_COUNT) {
			throw new IllegalArgumentException("main question number is out of range");
		}
		return mainQuestions.get(questionNo - 1);
	}

	public int maxFollowUps() {
		return expectedFollowUpLimit(difficulty);
	}

	private static int expectedFollowUpLimit(InterviewDifficulty difficulty) {
		return difficulty == InterviewDifficulty.CHALLENGE ? 2 : 1;
	}
}
