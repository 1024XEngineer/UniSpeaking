package com.unispeaking.domain.vo.scene;

import java.util.Objects;

/**
 * Transient inputs for generating a question plan.
 */
public record InterviewQuestionPlanGenerationInput(
		InterviewDifficulty difficulty,
		TargetRoleSummary targetRoleSummary,
		String resumeText) {

	public InterviewQuestionPlanGenerationInput {
		difficulty = Objects.requireNonNull(difficulty, "difficulty must not be null");
		targetRoleSummary = Objects.requireNonNull(
				targetRoleSummary,
				"targetRoleSummary must not be null");
	}

	@Override
	public String toString() {
		return "InterviewQuestionPlanGenerationInput[difficulty=" + difficulty
				+ ", targetRoleSummary=<redacted>, resumeText=<redacted>]";
	}
}
