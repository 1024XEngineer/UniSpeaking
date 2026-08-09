package com.unispeaking.common.prompt.interview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.dto.scene.InterviewContext;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterviewPromptBuilderTest {

	private final InterviewPromptBuilder builder = new InterviewPromptBuilder();

	@Test
	void buildsSystemPromptWithRoleMaterialFlowAndDifficultyRules() {
		InterviewContext context = new InterviewContext(
				"候选人有三年后端经验。",
				"负责支付系统设计。",
				List.of("自我介绍", "项目经历", "技术栈", "职业规划"));

		String prompt = builder.build(context, InterviewDifficulty.STANDARD);

		assertTrue(prompt.contains("候选人有三年后端经验。"));
		assertTrue(prompt.contains("负责支付系统设计。"));
		assertTrue(prompt.contains("自我介绍"));
		assertTrue(prompt.contains("4 topics"));
		assertTrue(prompt.contains("STANDARD"));
		assertTrue(prompt.contains("at most one moderate follow-up question per topic"));
		assertTrue(prompt.contains("Never evaluate, score, grade"));
		assertTrue(prompt.contains("Never invent facts"));
	}

	@Test
	void hardDifficultyAllowsTwoDeeperFollowUps() {
		InterviewContext context = new InterviewContext(
				"候选人有三年后端经验。",
				"负责支付系统设计。",
				List.of("自我介绍", "项目经历", "技术栈", "职业规划"));

		String prompt = builder.build(context, InterviewDifficulty.HARD);

		assertTrue(prompt.contains("at most two deeper follow-up questions per topic"));
	}

	@Test
	void easyDifficultyAllowsOneShallowFollowUp() {
		InterviewContext context = new InterviewContext(
				"候选人有三年后端经验。",
				"负责支付系统设计。",
				List.of("自我介绍", "项目经历", "技术栈", "职业规划"));

		String prompt = builder.build(context, InterviewDifficulty.EASY);

		assertTrue(prompt.contains("at most one shallow follow-up question per topic"));
	}

	@Test
	void neverEmitsJsonOrScoringInstructions() {
		InterviewContext context = new InterviewContext(
				"候选人有三年后端经验。",
				"负责支付系统设计。",
				List.of("自我介绍", "项目经历", "技术栈", "职业规划"));

		String prompt = builder.build(context, InterviewDifficulty.EASY);

		assertFalse(prompt.contains("```"));
		assertFalse(prompt.contains("interview_topics"));
		assertFalse(prompt.contains("candidate_overview"));
		assertFalse(prompt.contains("shouldEnd"));
	}

	@Test
	void rejectsEmptyTopicList() {
		InterviewContext context = new InterviewContext(
				"候选人有三年后端经验。",
				"负责支付系统设计。",
				List.of());

		assertThrows(
				IllegalArgumentException.class,
				() -> builder.build(context, InterviewDifficulty.EASY));
	}
}
