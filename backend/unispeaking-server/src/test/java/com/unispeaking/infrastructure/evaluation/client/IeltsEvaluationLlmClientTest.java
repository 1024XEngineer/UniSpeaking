package com.unispeaking.infrastructure.evaluation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.prompt.evaluation.IeltsEvaluationPromptBuilder;
import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.common.evaluation.EvaluationProviderFailureTranslator;
import com.unispeaking.provider.AiProviderRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class IeltsEvaluationLlmClientTest {

	@Test
	void retriesMalformedProviderJsonAndReturnsPartAssessment() {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.executeLlmTaskRouted(anyString(), isNull()))
				.thenReturn(routed("not-json"), routed(validPart2Json()));
		IeltsEvaluationLlmClient client = new IeltsEvaluationLlmClient(
				registry,
				new EvaluationProviderFailureTranslator(),
				new IeltsEvaluationPromptBuilder(new ObjectMapper()),
				new ObjectMapper());

		var result = client.assessPart(
				IeltsPart.PART_2,
				"CANDIDATE: I would like to describe a memorable trip.",
				"Describe a memorable trip.",
				null);

		assertEquals(IeltsPart.PART_2, result.part());
		assertEquals(new BigDecimal("6.0"), result.fluencyCoherenceBand());
		verify(registry, times(2)).executeLlmTaskRouted(anyString(), isNull());
	}

	private AiProviderRegistry.RoutedResult<String> routed(String response) {
		return new AiProviderRegistry.RoutedResult<>(
				"test-model",
				"test-provider",
				AiCapability.LLM,
				response);
	}

	private String validPart2Json() {
		return """
				{
				  "part":"PART_2",
				  "assessment_type":"DIAGNOSTIC",
				  "fluency_coherence":{"band":6,"strengths":["内容相关"],"issues":["衔接有限"],"evidence":["I would like"]},
				  "lexical_resource":{"band":6,"strengths":["词义清楚"],"issues":[],"evidence":["memorable trip"]},
				  "grammatical_range_accuracy":{"band":6,"strengths":["基本准确"],"issues":[],"evidence":["I would like to describe"]},
				  "pronunciation":{"band":null,"reason":"Pronunciation must be assessed from audio."},
				  "text_diagnostic_band":6,
				  "priority_improvements":[],
				  "summary_zh":"能够完成话题描述。",
				  "confidence":"MEDIUM"
				}
				""";
	}
}
