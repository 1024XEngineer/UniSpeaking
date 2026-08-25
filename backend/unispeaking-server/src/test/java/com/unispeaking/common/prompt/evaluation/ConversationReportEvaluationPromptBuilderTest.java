package com.unispeaking.common.prompt.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.dto.session.Message;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
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

	@Test
	void rejectsMissingDialogueAndInvalidMessages() {
		ConversationReportEvaluationPromptBuilder builder = builder(new ObjectMapper());
		List<List<Message>> invalid = List.of(
				List.of(),
				Arrays.asList((Message) null),
				List.of(new Message(null, "text", null)),
				List.of(new Message(-1, "text", null)),
				List.of(new Message(2, "text", null)),
				List.of(new Message(0, null, null)),
				List.of(new Message(1, " \t", null)));

		EvaluationException nullDialogue = assertThrows(
				EvaluationException.class, () -> builder.build(null));
		assertEquals(EvaluationErrorCode.INVALID_REQUEST, nullDialogue.errorCode());
		for (List<Message> dialogue : invalid) {
			EvaluationException exception = assertThrows(
					EvaluationException.class, () -> builder.build(dialogue));
			assertEquals(EvaluationErrorCode.INVALID_REQUEST, exception.errorCode());
		}
	}

	@Test
	void escapesBoundaryCharactersInSerializedDialogue() {
		String prompt = builder(new ObjectMapper()).build(
				List.of(new Message(1, "A&B<C>D", null)));

		assertTrue(prompt.contains("A\\u0026B\\u003CC\\u003ED"));
		assertFalse(prompt.contains("A&B<C>D"));
	}

	@Test
	void validatesDependenciesAndWrapsSerializationFailure() {
		ConversationReportEvaluationPromptTemplateLoader loader =
				new ConversationReportEvaluationPromptTemplateLoader();
		assertThrows(NullPointerException.class,
				() -> new ConversationReportEvaluationPromptBuilder(null, loader));
		assertThrows(NullPointerException.class,
				() -> new ConversationReportEvaluationPromptBuilder(new ObjectMapper(), null));

		ObjectMapper mapper = spy(new ObjectMapper());
		JacksonException cause = new JacksonException("failed") { };
		when(mapper.writeValueAsString(org.mockito.ArgumentMatchers.any())).thenThrow(cause);
		EvaluationException exception = assertThrows(
				EvaluationException.class,
				() -> builder(mapper).build(List.of(new Message(0, "text", null))));
		assertEquals(EvaluationErrorCode.PROMPT_TEMPLATE_INVALID, exception.errorCode());
		assertEquals(cause, exception.getCause());
	}

	private ConversationReportEvaluationPromptBuilder builder(ObjectMapper mapper) {
		return new ConversationReportEvaluationPromptBuilder(
				mapper, new ConversationReportEvaluationPromptTemplateLoader());
	}
}
