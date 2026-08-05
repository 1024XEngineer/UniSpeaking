package com.unispeaking.domain.vo.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewGenerationBoundaryTest {

	@Test
	void excludesResumeFromTargetRoleSummaryGenerationInput() {
		assertArrayEquals(
				new String[] {"jobTitle", "jobDescription"},
				Arrays.stream(TargetRoleSummaryGenerationInput.class.getRecordComponents())
						.map(component -> component.getName())
						.toArray(String[]::new));
	}

	@Test
	void separatesSummaryAndQuestionPlanGenerationContracts() {
		InterviewPreparedMaterials materials = new InterviewPreparedMaterials(
				"Secret role",
				"Secret JD",
				"Secret resume");
		TargetRoleSummary summary = new TargetRoleSummary(
				"Overview",
				List.of("Responsibility"),
				List.of("Skill"),
				List.of("Qualification"));

		TargetRoleSummaryGenerationInput summaryInput = materials.targetRoleSummaryInput();
		InterviewQuestionPlanGenerationInput planInput = materials.questionPlanInput(
				InterviewDifficulty.STANDARD,
				summary);
		TargetRoleSummaryGenerator summaryGenerator = input -> summary;
		InterviewQuestionPlanGenerator planGenerator = input ->
				InterviewQuestionPlan.fromGeneratedMainQuestions(
						input.difficulty(),
						List.of("Q1", "Q2", "Q3", "Q4", "Q5"));

		assertEquals(summary, summaryGenerator.generate(summaryInput));
		assertEquals("Secret resume", planInput.resumeText());
		assertEquals(5, planGenerator.generate(planInput).mainQuestions().size());
		assertFalse(summaryInput.toString().contains("Secret"));
		assertFalse(planInput.toString().contains("Secret"));
	}
}
