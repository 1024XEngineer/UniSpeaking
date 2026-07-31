package com.unispeaking.common.prompt.evaluation;

import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 将完整带角色对话安全注入五维报告 Prompt。
 */
@Service
public final class ConversationReportEvaluationPromptBuilder {

	private final ObjectMapper objectMapper;
	private final String template;

	public ConversationReportEvaluationPromptBuilder(
			ObjectMapper objectMapper,
			ConversationReportEvaluationPromptTemplateLoader templateLoader) {
		this.objectMapper = Objects.requireNonNull(
				objectMapper,
				"objectMapper must not be null");
		this.template = Objects.requireNonNull(
				templateLoader,
				"templateLoader must not be null").template();
	}

	public String build(List<Message> dialogue) {
		if (dialogue == null
				|| dialogue.isEmpty()
				|| dialogue.stream().anyMatch(message -> message == null)) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}

		ObjectNode root = objectMapper.createObjectNode();
		ArrayNode conversation = objectMapper.createArrayNode();
		for (Message message : dialogue) {
			if (message.owner() == null
					|| (message.owner() != 0 && message.owner() != 1)
					|| message.content() == null
					|| message.content().isBlank()) {
				throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
			}
			ObjectNode utterance = objectMapper.createObjectNode();
			utterance.put(
					"speaker",
					message.owner() == 0 ? "AI" : "LEARNER");
			utterance.put("text", message.content());
			conversation.add(utterance);
		}
		root.set("conversation", conversation);

		try {
			String inputJson = objectMapper.writeValueAsString(root);
			return template.replace(
					ConversationReportEvaluationPromptTemplateLoader
							.INPUT_PLACEHOLDER,
					escapeBoundary(inputJson));
		}
		catch (JacksonException exception) {
			throw new EvaluationException(
					EvaluationErrorCode.PROMPT_TEMPLATE_INVALID,
					null,
					exception);
		}
	}

	private static String escapeBoundary(String json) {
		return json
				.replace("&", "\\u0026")
				.replace("<", "\\u003C")
				.replace(">", "\\u003E");
	}
}
