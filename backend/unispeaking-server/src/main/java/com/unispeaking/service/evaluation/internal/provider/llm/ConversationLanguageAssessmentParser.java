package com.unispeaking.service.evaluation.internal.provider.llm;

import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import com.unispeaking.service.evaluation.internal.model.ConversationLanguageAssessment;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * 解析五维方案中 LLM 负责的 Grammar、Vocabulary 和 Text Naturalness。
 */
public final class ConversationLanguageAssessmentParser {

	private static final Set<String> ROOT_FIELDS = Set.of(
			"assessment_status",
			"scores",
			"confidence",
			"dimensions",
			"data_quality_notes");
	private static final Set<String> SCORE_FIELDS =
			Set.of("grammar", "vocabulary", "text_naturalness");
	private static final Set<String> DIMENSION_FIELDS =
			Set.of("grammar", "vocabulary", "text_naturalness");
	private static final BigDecimal MAX_SCORE = new BigDecimal("100");

	private final EvaluationJsonDocumentParser documentParser;

	public ConversationLanguageAssessmentParser(
			EvaluationJsonDocumentParser documentParser) {
		this.documentParser = Objects.requireNonNull(
				documentParser,
				"documentParser must not be null");
	}

	public ConversationLanguageAssessment parse(String assistantContent) {
		JsonNode root = documentParser.parseObject(assistantContent);
		requireExactFields(root, ROOT_FIELDS);
		String status = requireText(root, "assessment_status");
		if (!"ok".equals(status)) {
			if ("insufficient_data".equals(status)) {
				throw new EvaluationException(
						EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
			}
			throw invalid();
		}

		JsonNode scores = requireObject(root, "scores");
		requireExactFields(scores, SCORE_FIELDS);
		BigDecimal grammar = requireScore(scores, "grammar");
		BigDecimal vocabulary = requireScore(scores, "vocabulary");
		BigDecimal textNaturalness =
				requireScore(scores, "text_naturalness");
		requireConfidence(root.get("confidence"));
		requireTextArray(root.get("data_quality_notes"));

		JsonNode dimensions = requireObject(root, "dimensions");
		requireExactFields(dimensions, DIMENSION_FIELDS);
		List<String> strengths = new ArrayList<>();
		List<String> improvements = new ArrayList<>();
		collectFeedback(dimensions, "grammar", strengths, improvements);
		collectFeedback(dimensions, "vocabulary", strengths, improvements);
		collectFeedback(dimensions, "text_naturalness", strengths, improvements);

		String summary = "语法、词汇和文本自然度分别为 "
				+ display(grammar) + "、"
				+ display(vocabulary) + "、"
				+ display(textNaturalness) + " 分。";
		return new ConversationLanguageAssessment(
				grammar,
				vocabulary,
				textNaturalness,
				summary,
				List.copyOf(strengths),
				List.copyOf(improvements));
	}

	private void collectFeedback(
			JsonNode dimensions,
			String dimensionName,
			List<String> strengths,
			List<String> improvements) {
		JsonNode dimension = requireObject(dimensions, dimensionName);
		requireExactFields(dimension, Set.of("strengths", "improvements"));
		JsonNode strengthItems = requireArray(dimension, "strengths");
		JsonNode improvementItems = requireArray(dimension, "improvements");
		if (strengthItems.size() > 3 || improvementItems.size() > 3) {
			throw invalid();
		}
		for (JsonNode item : strengthItems) {
			requireObjectNode(item);
			String evidence = requireText(item, "evidence");
			String reason = requireText(item, "reason");
			requireChinese(reason);
			strengths.add(evidence + "：" + reason);
		}
		for (JsonNode item : improvementItems) {
			requireObjectNode(item);
			String evidence = requireText(item, "evidence");
			String reason = requireText(item, "reason");
			requireChinese(reason);
			String replacement = optionalText(item, "correction");
			if (replacement == null) {
				replacement = optionalText(item, "suggestion");
			}
			if (replacement == null) {
				throw incomplete();
			}
			improvements.add(
					evidence + " → " + replacement + "：" + reason);
		}
	}

	private BigDecimal requireScore(JsonNode parent, String fieldName) {
		JsonNode value = requireNode(parent, fieldName);
		if (!value.isNumber()) {
			throw invalid();
		}
		BigDecimal score = value.decimalValue();
		if (score.compareTo(BigDecimal.ZERO) < 0
				|| score.compareTo(MAX_SCORE) > 0
				|| Math.max(0, score.stripTrailingZeros().scale()) > 2) {
			throw invalid();
		}
		return score;
	}

	private void requireConfidence(JsonNode value) {
		if (value == null || !value.isNumber()) {
			throw incomplete();
		}
		BigDecimal confidence = value.decimalValue();
		if (confidence.compareTo(BigDecimal.ZERO) < 0
				|| confidence.compareTo(BigDecimal.ONE) > 0) {
			throw invalid();
		}
	}

	private void requireTextArray(JsonNode value) {
		if (value == null || !value.isArray()) {
			throw incomplete();
		}
		for (JsonNode item : value) {
			if (!item.isString() || item.asString().isBlank()) {
				throw invalid();
			}
			requireChinese(item.asString());
		}
	}

	private void requireChinese(String value) {
		boolean containsChinese = value.codePoints().anyMatch(
				codePoint -> Character.UnicodeScript.of(codePoint)
						== Character.UnicodeScript.HAN);
		if (!containsChinese) {
			throw invalid();
		}
	}

	private JsonNode requireObject(JsonNode parent, String fieldName) {
		JsonNode value = requireNode(parent, fieldName);
		requireObjectNode(value);
		return value;
	}

	private void requireObjectNode(JsonNode value) {
		if (value == null || !value.isObject()) {
			throw invalid();
		}
	}

	private JsonNode requireArray(JsonNode parent, String fieldName) {
		JsonNode value = requireNode(parent, fieldName);
		if (!value.isArray()) {
			throw invalid();
		}
		return value;
	}

	private JsonNode requireNode(JsonNode parent, String fieldName) {
		JsonNode value = parent.get(fieldName);
		if (value == null || value.isNull()) {
			throw incomplete();
		}
		return value;
	}

	private String requireText(JsonNode parent, String fieldName) {
		String value = optionalText(parent, fieldName);
		if (value == null) {
			throw incomplete();
		}
		return value;
	}

	private String optionalText(JsonNode parent, String fieldName) {
		JsonNode value = parent.get(fieldName);
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isString()) {
			throw invalid();
		}
		String text = value.asString().trim();
		if (text.isEmpty()) {
			throw incomplete();
		}
		return text;
	}

	private void requireExactFields(JsonNode object, Set<String> fields) {
		requireObjectNode(object);
		if (!Set.copyOf(object.propertyNames()).equals(fields)) {
			throw invalid();
		}
	}

	private String display(BigDecimal score) {
		return score.stripTrailingZeros().toPlainString();
	}

	private EvaluationException invalid() {
		return new EvaluationException(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
	}

	private EvaluationException incomplete() {
		return new EvaluationException(
				EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
	}
}
