package com.unispeaking.service.prompt.evaluation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unispeaking.domain.dto.session.Message;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConversationReportEvaluationPromptBuilderTest {

	@Test
	void labelsOnlyLearnerForAssessmentAndKeepsAiAsContext() {
		ConversationReportEvaluationPromptBuilder builder =
				new ConversationReportEvaluationPromptBuilder(
						new ObjectMapper(),
						new ConversationReportEvaluationPromptTemplateLoader());

		String prompt = builder.build(List.of(
				new Message(0, "How are you?", null),
				new Message(1, "I am doing well.", null)));

		assertTrue(prompt.contains("\"speaker\":\"AI\""));
		assertTrue(prompt.contains("\"speaker\":\"LEARNER\""));
		assertFalse(prompt.contains("taskScoringRequired"));
	}
}
