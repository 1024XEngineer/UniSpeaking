package com.unispeaking.common.evaluation.parser;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.evaluation.model.TurnLanguageFeedback;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * 将严格评价 JSON 转换为单轮语言反馈内部模型。
 *
 * <p>仅接受 Prompt 约定的两个字段，不忽略未知字段，也不对字段名进行别名
 * 或大小写兼容，确保 Prompt 和解析契约保持一致。</p>
 */
public final class TurnLanguageFeedbackParser {

	private static final String FEEDBACK_SUMMARY = "feedbackSummary";
	private static final String SUGGESTED_EXPRESSION = "suggestedExpression";
	private static final Set<String> ALLOWED_FIELDS =
			Set.of(FEEDBACK_SUMMARY, SUGGESTED_EXPRESSION);

	private final EvaluationJsonDocumentParser documentParser;

	/**
	 * 创建复用统一严格 JSON 文档规则的单轮解析器。
	 *
	 * @param documentParser 负责围栏、重复字段和完整文档校验的解析器
	 */
	public TurnLanguageFeedbackParser(
			EvaluationJsonDocumentParser documentParser) {
		this.documentParser =
				Objects.requireNonNull(documentParser, "documentParser must not be null");
	}

	/**
	 * 解析并校验单轮反馈的字段白名单、类型和非空文本。
	 *
	 * @param assistantContent 从百炼响应中提取的 assistant content
	 * @return 去除字段首尾空白后的单轮语言反馈
	 */
	public TurnLanguageFeedback parse(String assistantContent) {
		JsonNode root = documentParser.parseObject(assistantContent);
		validateFieldWhitelist(root);
		String feedbackSummary = requireNonBlankText(root, FEEDBACK_SUMMARY);
		String suggestedExpression =
				requireNonBlankText(root, SUGGESTED_EXPRESSION);
		if (!containsChinese(feedbackSummary)) {
			throw invalidResponse();
		}
		return new TurnLanguageFeedback(feedbackSummary, suggestedExpression);
	}

	private void validateFieldWhitelist(JsonNode root) {
		for (String fieldName : root.propertyNames()) {
			if (!ALLOWED_FIELDS.contains(fieldName)) {
				throw invalidResponse();
			}
		}
	}

	private String requireNonBlankText(JsonNode root, String fieldName) {
		JsonNode value = root.get(fieldName);
		if (value == null || value.isNull()) {
			throw incompleteResponse();
		}
		if (!value.isTextual()) {
			throw invalidResponse();
		}
		String text = value.asString().trim();
		if (text.isEmpty()) {
			throw incompleteResponse();
		}
		return text;
	}

	private boolean containsChinese(String value) {
		return value.codePoints().anyMatch(
				codePoint -> Character.UnicodeScript.of(codePoint)
						== Character.UnicodeScript.HAN);
	}

	private EvaluationException invalidResponse() {
		return new EvaluationException(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
	}

	private EvaluationException incompleteResponse() {
		return new EvaluationException(
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
	}
}
