package com.unispeaking.infrastructure.evaluation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.evaluation.EvaluationProviderFailureTranslator;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.prompt.evaluation.ConversationReportEvaluationPromptBuilder;
import com.unispeaking.common.prompt.evaluation.DialogueTurnEvaluationPromptBuilder;
import com.unispeaking.common.prompt.evaluation.DialogueTurnEvaluationPromptInput;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.provider.AiInvocationContext;
import com.unispeaking.provider.AiProviderRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EvaluationLlmClientTest {

	@Test
	void assessesDialogueAndTurnWithAndWithoutInvocationContext() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed(validReportJson()));
		when(registry.executeLlmTaskRouted(any(AiInvocationContext.class), anyString(), isNull()))
				.thenReturn(routed(validReportJson()));
		EvaluationLlmClient client = client(registry);

		var report = client.assessDialogue(List.of(new Message(0, "hello", null)));
		assertEquals("80", report.grammarScore().toString());
		var context = AiInvocationContext.create("user", "session", "evaluation");
		assertEquals("80", client.assessDialogue(List.of(new Message(1, "answer", null)), context)
				.grammarScore().toString());
		verify(registry).executeLlmTaskRouted(eq(context), anyString(), isNull());

		when(registry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed("{\"feedbackSummary\":\"表达清楚\",\"suggestedExpression\":\"A natural answer.\"}"));
		var input = new DialogueTurnEvaluationPromptInput(
				"FREE_CHAT", null, null, null, null, List.of(), null, "I agree.");
		assertEquals("表达清楚", client.assessTurn(input).feedbackSummary());
	}

	@Test
	void translatesProviderBusinessFailuresAndPropagatesParserFailures() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTaskRouted(anyString(), isNull()))
				.thenThrow(new BusinessException("AI_PROVIDER_ROUTE_NOT_FOUND", "secret detail"));
		EvaluationLlmClient client = client(registry);
		EvaluationException translated = assertThrows(
				EvaluationException.class,
				() -> client.assessTurn(new DialogueTurnEvaluationPromptInput(
						"FREE_CHAT", null, null, null, null, List.of(), null, "I agree.")));
		assertEquals(EvaluationErrorCode.PROVIDER_NOT_CONFIGURED, translated.errorCode());

		AiProviderRegistry invalidRegistry = mock(AiProviderRegistry.class);
		doReturn(routed("not-json")).when(invalidRegistry)
				.executeLlmTaskRouted(anyString(), isNull());
		EvaluationException invalid = assertThrows(
				EvaluationException.class,
				() -> client(invalidRegistry).assessTurn(new DialogueTurnEvaluationPromptInput(
						"FREE_CHAT", null, null, null, null, List.of(), null, "I agree.")));
		assertEquals(EvaluationErrorCode.PROVIDER_RESPONSE_INVALID, invalid.errorCode());
	}

	@Test
	void mapsAsyncExecutionFailuresToProviderCallFailed() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTaskRouted(anyString(), isNull()))
				.thenThrow(new RuntimeException("network failure"));
		EvaluationException failure = assertThrows(
				EvaluationException.class,
				() -> client(registry).assessDialogue(
						List.of(new Message(1, "answer", null))));
		assertEquals(EvaluationErrorCode.PROVIDER_CALL_FAILED, failure.errorCode());
	}

	private EvaluationLlmClient client(AiProviderRegistry registry) {
		return new EvaluationLlmClient(
				registry,
				new EvaluationProviderFailureTranslator(),
				new ConversationReportEvaluationPromptBuilder(
						new ObjectMapper(),
						new com.unispeaking.common.prompt.evaluation.ConversationReportEvaluationPromptTemplateLoader()),
				new DialogueTurnEvaluationPromptBuilder(
						new ObjectMapper(),
						new com.unispeaking.common.prompt.evaluation.DialogueTurnEvaluationPromptTemplateLoader()),
				new ObjectMapper());
	}

	private AiProviderRegistry.RoutedResult<String> routed(String response) {
		return new AiProviderRegistry.RoutedResult<>("model", "provider",
				com.unispeaking.domain.vo.provider.AiCapability.LLM, response);
	}

	private String validReportJson() {
		return """
				{"assessment_status":"ok","scores":{"grammar":80,"vocabulary":75,"text_naturalness":70},"confidence":0.8,
				"dimensions":{"grammar":{"strengths":[{"evidence":"准确","reason":"表达清楚"}],"improvements":[{"evidence":"word","correction":"更自然","reason":"更自然"}]},
				"vocabulary":{"strengths":[],"improvements":[]},"text_naturalness":{"strengths":[],"improvements":[]}},"data_quality_notes":["信息充分"]}
				""";
	}
}
