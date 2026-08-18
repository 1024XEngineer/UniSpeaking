package com.unispeaking.infrastructure.evaluation.client;

import com.unispeaking.common.evaluation.model.IeltsTextAssessment;
import com.unispeaking.common.evaluation.parser.EvaluationJsonDocumentParser;
import com.unispeaking.common.evaluation.parser.IeltsTextAssessmentParser;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.prompt.evaluation.IeltsEvaluationPromptBuilder;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.common.evaluation.EvaluationProviderFailureTranslator;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.AiInvocationContext;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

@Component
public final class IeltsEvaluationLlmClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(
			IeltsEvaluationLlmClient.class);
	private static final int MAX_PARSE_ATTEMPTS = 2;

	private final AiProviderRegistry registry;
	private final EvaluationProviderFailureTranslator failureTranslator;
	private final IeltsEvaluationPromptBuilder promptBuilder;
	private final IeltsTextAssessmentParser parser;

	public IeltsEvaluationLlmClient(
			AiProviderRegistry registry,
			EvaluationProviderFailureTranslator failureTranslator,
			IeltsEvaluationPromptBuilder promptBuilder,
			ObjectMapper objectMapper) {
		this.registry = registry;
		this.failureTranslator = failureTranslator;
		this.promptBuilder = promptBuilder;
		this.parser = new IeltsTextAssessmentParser(
				new EvaluationJsonDocumentParser(objectMapper));
	}

	public IeltsTextAssessment assessPart(
			IeltsPart part,
			String transcript,
			String cueCard,
			String speechMetrics) {
		return assessPart(part, transcript, cueCard, speechMetrics, null);
	}

	public IeltsTextAssessment assessPart(
			IeltsPart part, String transcript, String cueCard, String speechMetrics,
			AiInvocationContext context) {
		return executeAndParse(promptBuilder.buildPart(
				part,
				transcript,
				cueCard,
				speechMetrics), part, context);
	}

	public IeltsTextAssessment assessFullTest(
			String transcript,
			String speechMetrics,
			String pronunciationBand) {
		return executeAndParse(promptBuilder.buildFinal(
				transcript,
				speechMetrics,
				pronunciationBand), null, null);
	}

	private IeltsTextAssessment executeAndParse(
			String prompt,
			IeltsPart expectedPart,
			AiInvocationContext context) {
		EvaluationException lastFailure = null;
		for (int attempt = 1; attempt <= MAX_PARSE_ATTEMPTS; attempt++) {
			String effectivePrompt = attempt == 1
					? prompt
					: prompt + "\n\n# JSON correction retry\n"
							+ "The previous response could not be parsed. Return exactly "
							+ "one complete JSON object matching the required schema. "
							+ "Use numeric whole bands, include every required field, "
							+ "and output no Markdown or commentary.";
			try {
				return parser.parse(execute(effectivePrompt, context), expectedPart);
			}
			catch (EvaluationException exception) {
				if (!isRetryableParseFailure(exception)
						|| attempt == MAX_PARSE_ATTEMPTS) {
					throw exception;
				}
				lastFailure = exception;
				LOGGER.warn(
						"IELTS evaluation JSON rejected; retrying part={} code={}",
						expectedPart == null ? "FULL_TEST" : expectedPart,
						exception.errorCode().code());
			}
		}
		throw lastFailure;
	}

	private boolean isRetryableParseFailure(EvaluationException exception) {
		return exception.errorCode() == EvaluationErrorCode.PROVIDER_RESPONSE_INVALID
				|| exception.errorCode()
						== EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE;
	}

	private String execute(String prompt, AiInvocationContext context) {
		try {
			return context == null
					? registry.executeLlmTaskRouted(prompt, null).response()
					: registry.executeLlmTaskRouted(context, prompt, null).response();
		}
		catch (BusinessException exception) {
			throw failureTranslator.translate(exception);
		}
	}
}
