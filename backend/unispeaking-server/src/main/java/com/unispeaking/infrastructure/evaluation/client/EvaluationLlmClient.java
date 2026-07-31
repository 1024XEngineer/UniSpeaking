package com.unispeaking.infrastructure.evaluation.client;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.evaluation.model.ConversationLanguageAssessment;
import com.unispeaking.common.evaluation.model.TurnLanguageFeedback;
import com.unispeaking.common.evaluation.parser.ConversationLanguageAssessmentParser;
import com.unispeaking.common.evaluation.parser.EvaluationJsonDocumentParser;
import com.unispeaking.common.evaluation.parser.TurnLanguageFeedbackParser;
import com.unispeaking.common.prompt.evaluation.ConversationReportEvaluationPromptBuilder;
import com.unispeaking.common.prompt.evaluation.DialogueTurnEvaluationPromptBuilder;
import com.unispeaking.common.prompt.evaluation.DialogueTurnEvaluationPromptInput;
import com.unispeaking.infrastructure.evaluation.provider.EvaluationProviderFailureTranslator;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 评分模块调用 LLM 的受控边界。
 */
@Component
public final class EvaluationLlmClient {

	private static final Duration REPORT_TIMEOUT = Duration.ofSeconds(20);

	private final AiProviderRegistry registry;
	private final EvaluationProviderFailureTranslator failureTranslator;
	private final ConversationReportEvaluationPromptBuilder reportPromptBuilder;
	private final DialogueTurnEvaluationPromptBuilder turnPromptBuilder;
	private final ConversationLanguageAssessmentParser reportParser;
	private final TurnLanguageFeedbackParser turnParser;

	public EvaluationLlmClient(
			AiProviderRegistry registry,
			EvaluationProviderFailureTranslator failureTranslator,
			ConversationReportEvaluationPromptBuilder reportPromptBuilder,
			DialogueTurnEvaluationPromptBuilder turnPromptBuilder,
			ObjectMapper objectMapper) {
		this.registry = Objects.requireNonNull(registry, "registry must not be null");
		this.failureTranslator = Objects.requireNonNull(
				failureTranslator,
				"failureTranslator must not be null");
		this.reportPromptBuilder = Objects.requireNonNull(
				reportPromptBuilder,
				"reportPromptBuilder must not be null");
		this.turnPromptBuilder = Objects.requireNonNull(
				turnPromptBuilder,
				"turnPromptBuilder must not be null");
		EvaluationJsonDocumentParser documentParser =
				new EvaluationJsonDocumentParser(
						Objects.requireNonNull(
								objectMapper,
								"objectMapper must not be null"));
		this.reportParser = new ConversationLanguageAssessmentParser(documentParser);
		this.turnParser = new TurnLanguageFeedbackParser(documentParser);
	}

	public ConversationLanguageAssessment assessDialogue(List<Message> dialogue) {
		CompletableFuture<ConversationLanguageAssessment> task =
				CompletableFuture.supplyAsync(() -> reportParser.parse(
						execute(reportPromptBuilder.build(dialogue))));
		try {
			return task.get(REPORT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException exception) {
			task.cancel(true);
			throw new EvaluationException(
					EvaluationErrorCode.PROVIDER_CALL_FAILED);
		}
		catch (InterruptedException exception) {
			task.cancel(true);
			Thread.currentThread().interrupt();
			throw new EvaluationException(
					EvaluationErrorCode.PROVIDER_CALL_FAILED);
		}
		catch (ExecutionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof EvaluationException evaluationException) {
				throw evaluationException;
			}
			throw new EvaluationException(
					EvaluationErrorCode.PROVIDER_CALL_FAILED);
		}
	}

	public TurnLanguageFeedback assessTurn(
			DialogueTurnEvaluationPromptInput input) {
		return turnParser.parse(execute(turnPromptBuilder.build(input)));
	}

	private String execute(String prompt) {
		try {
			return registry.executeLlmTaskRouted(prompt, null).response();
		}
		catch (BusinessException exception) {
			throw failureTranslator.translate(exception);
		}
	}
}
