package com.unispeaking.domain.vo.scene;

/**
 * Provider-independent question-plan generation boundary.
 */
@FunctionalInterface
public interface InterviewQuestionPlanGenerator {

	InterviewQuestionPlan generate(InterviewQuestionPlanGenerationInput input);
}
