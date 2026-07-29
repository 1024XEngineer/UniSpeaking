package com.unispeaking.service.evaluation.internal.client;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.evaluation.internal.model.ConversationLanguageAssessment;
import com.unispeaking.service.evaluation.internal.model.TurnLanguageFeedback;
import com.unispeaking.service.evaluation.internal.provider.llm.ConversationLanguageAssessmentParser;
import com.unispeaking.service.evaluation.internal.provider.llm.EvaluationJsonDocumentParser;
import com.unispeaking.service.evaluation.internal.provider.llm.TurnLanguageFeedbackParser;
import com.unispeaking.service.prompt.evaluation.ConversationReportEvaluationPromptBuilder;
import com.unispeaking.service.prompt.evaluation.DialogueTurnEvaluationPromptBuilder;
import com.unispeaking.service.prompt.evaluation.DialogueTurnEvaluationPromptInput;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 评分模块调用 LLM 的受控边界。
 */
@Component
public final class EvaluationLlmClient {

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
		return reportParser.parse(execute(reportPromptBuilder.build(dialogue)));
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
