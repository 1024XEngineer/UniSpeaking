package com.unispeaking.common.prompt.evaluation;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 将评价上下文、历史对话和当前回答安全注入已审核的单轮评价模板。
 */
@Service
public final class DialogueTurnEvaluationPromptBuilder {

	private static final String AI_SPEAKER = "AI";
	private static final String USER_SPEAKER = "USER";

	private final ObjectMapper objectMapper;
	private final String template;

	/**
	 * 创建复用项目 Jackson 配置及单次加载模板的 Prompt 构建器。
	 */
	public DialogueTurnEvaluationPromptBuilder(
			ObjectMapper objectMapper,
			DialogueTurnEvaluationPromptTemplateLoader templateLoader) {
		this.objectMapper = Objects.requireNonNull(
				objectMapper,
				"objectMapper must not be null");
		this.template = Objects.requireNonNull(
				templateLoader,
				"templateLoader must not be null").template();
	}

	/**
	 * 构造只评价当前 transcript 的完整单轮语言评价 Prompt。
	 *
	 * @param input 已完成基础规范化的评价输入
	 * @return 可直接提交给 LLM Provider 的完整 Prompt
	 */
	public String build(DialogueTurnEvaluationPromptInput input) {
		if (input == null) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}

		ObjectNode root = objectMapper.createObjectNode();
		root.set("evaluationContext", buildEvaluationContext(input));
		root.set("previousUtterances", buildPreviousUtterances(input));
		root.put("currentTranscript", input.currentTranscript());
		root.set("recommendedExpressions", buildRecommendedExpressions(input));

		String inputJson;
		try {
			inputJson = objectMapper.writeValueAsString(root);
		}
		catch (JacksonException exception) {
			throw new EvaluationException(
					EvaluationErrorCode.PROMPT_TEMPLATE_INVALID,
					null,
					exception);
		}

		return template.replace(
				DialogueTurnEvaluationPromptTemplateLoader.INPUT_PLACEHOLDER,
				escapePromptBoundaryCharacters(inputJson));
	}

	private ArrayNode buildRecommendedExpressions(
			DialogueTurnEvaluationPromptInput input) {
		ArrayNode expressions = objectMapper.createArrayNode();
		input.recommendedExpressions().forEach(expression -> {
			ObjectNode item = objectMapper.createObjectNode();
			item.put("type", expression.type());
			item.put("expression", expression.expression());
			item.put("translation", expression.translation());
			item.put("usageNote", expression.usageNote());
			expressions.add(item);
		});
		return expressions;
	}

	private ObjectNode buildEvaluationContext(
			DialogueTurnEvaluationPromptInput input) {
		ObjectNode context = objectMapper.createObjectNode();
		context.put("practiceType", input.practiceMode());
		putNullable(context, "background", input.background());
		putNullable(context, "aiRole", input.aiRole());
		putNullable(context, "userRole", input.userRole());
		putNullable(context, "learningGoal", input.learningGoal());
		return context;
	}

	private ArrayNode buildPreviousUtterances(
			DialogueTurnEvaluationPromptInput input) {
		List<DialogueTurnEvaluationHistory> orderedHistory =
				new ArrayList<>(input.previousTurns());
		orderedHistory.sort(
				Comparator.comparingInt(
						DialogueTurnEvaluationHistory::utteranceNo));
		validateUniqueUtteranceNumbers(orderedHistory);

		ArrayNode utterances = objectMapper.createArrayNode();
		for (DialogueTurnEvaluationHistory history : orderedHistory) {
			if (history.aiText() != null) {
				addUtterance(utterances, AI_SPEAKER, history.aiText());
			}
			addUtterance(utterances, USER_SPEAKER, history.transcript());
		}
		if (input.aiText() != null) {
			addUtterance(utterances, AI_SPEAKER, input.aiText());
		}
		return utterances;
	}

	private static void validateUniqueUtteranceNumbers(
			List<DialogueTurnEvaluationHistory> orderedHistory) {
		for (int index = 1; index < orderedHistory.size(); index++) {
			int previousNumber =
					orderedHistory.get(index - 1).utteranceNo();
			int currentNumber = orderedHistory.get(index).utteranceNo();
			if (previousNumber == currentNumber) {
				throw new EvaluationException(
						EvaluationErrorCode.INVALID_REQUEST);
			}
		}
	}

	private void addUtterance(
			ArrayNode utterances,
			String speaker,
			String text) {
		ObjectNode utterance = objectMapper.createObjectNode();
		utterance.put("speaker", speaker);
		utterance.put("text", text);
		utterances.add(utterance);
	}

	private static void putNullable(
			ObjectNode target,
			String fieldName,
			String value) {
		if (value == null) {
			target.putNull(fieldName);
		}
		else {
			target.put(fieldName, value);
		}
	}

	/*
	 * JSON 自身会转义引号、反斜杠和换行；额外转义标签相关字符，防止输入中的
	 * </EVALUATION_INPUT> 提前闭合模板边界。Unicode 转义仍是合法 JSON。
	 */
	private static String escapePromptBoundaryCharacters(String json) {
		return json
				.replace("&", "\\u0026")
				.replace("<", "\\u003C")
				.replace(">", "\\u003E");
	}
}
