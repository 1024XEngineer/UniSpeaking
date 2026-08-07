package com.unispeaking.common.evaluation.parser;

import com.unispeaking.common.evaluation.model.IeltsTextAssessment;
import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.vo.scene.IeltsPart;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class IeltsTextAssessmentParser {

	private final EvaluationJsonDocumentParser documentParser;

	public IeltsTextAssessmentParser(
			EvaluationJsonDocumentParser documentParser) {
		this.documentParser = documentParser;
	}

	public IeltsTextAssessment parse(
			String assistantContent,
			IeltsPart expectedPart) {
		JsonNode root = documentParser.parseObject(assistantContent);
		String expectedAssessmentType = expectedPart == null
				? "FINAL"
				: "DIAGNOSTIC";
		if (!expectedAssessmentType.equals(text(root, "assessment_type"))) {
			throw invalid();
		}
		String partValue = text(root, "part");
		if (expectedPart != null && !expectedPart.name().equals(partValue)) {
			throw invalid();
		}
		if (expectedPart == null && !"FULL_TEST".equals(partValue)) {
			throw invalid();
		}
		JsonNode fc = object(root, "fluency_coherence");
		JsonNode lr = object(root, "lexical_resource");
		JsonNode gra = object(root, "grammatical_range_accuracy");
		List<String> strengths = new ArrayList<>();
		strengths.addAll(textArray(fc, "strengths"));
		strengths.addAll(textArray(lr, "strengths"));
		strengths.addAll(textArray(gra, "strengths"));
		List<String> improvements = priorityImprovements(root);
		if (improvements.isEmpty()) {
			improvements.addAll(textArray(fc, "issues"));
			improvements.addAll(textArray(lr, "issues"));
			improvements.addAll(textArray(gra, "issues"));
		}
		return new IeltsTextAssessment(
				expectedPart,
				wholeBand(fc, "band"),
				wholeBand(lr, "band"),
				wholeBand(gra, "band"),
				criterionReason(fc, "流利度与连贯性"),
				criterionReason(lr, "词汇资源"),
				criterionReason(gra, "语法多样性与准确性"),
				text(root, "summary_zh"),
				strengths.stream().limit(6).toList(),
				improvements.stream().limit(3).toList(),
				text(root, "confidence"));
	}

	private String criterionReason(JsonNode node, String label) {
		JsonNode reason = node.get("reason_zh");
		if (reason != null && reason.isTextual()
				&& !reason.asString().isBlank()) {
			return reason.asString().strip();
		}
		List<String> strengths = textArray(node, "strengths");
		List<String> issues = textArray(node, "issues");
		List<String> evidence = textArray(node, "evidence");
		StringBuilder result = new StringBuilder(label)
				.append("评分为 ")
				.append(wholeBand(node, "band").toPlainString())
				.append("：");
		if (!strengths.isEmpty()) result.append(strengths.getFirst());
		if (!issues.isEmpty()) {
			if (!strengths.isEmpty()) result.append("；但");
			result.append(issues.getFirst());
		}
		if (!evidence.isEmpty()) {
			result.append("。回答中的具体依据包括“")
					.append(evidence.getFirst())
					.append("”。");
		}
		return result.toString();
	}

	private BigDecimal wholeBand(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || (!value.isNumber() && !value.isTextual())) {
			throw invalid();
		}
		BigDecimal band;
		try {
			band = value.isNumber()
					? value.decimalValue()
					: new BigDecimal(value.asString().strip());
		}
		catch (NumberFormatException exception) {
			throw invalid();
		}
		if (band.compareTo(BigDecimal.ZERO) < 0
				|| band.compareTo(BigDecimal.valueOf(9)) > 0) {
			throw invalid();
		}
		// Criterion scores use whole bands. If the provider nevertheless returns
		// an in-between value, IELTS's lower-band rule is deterministic.
		return band.setScale(0, RoundingMode.FLOOR).setScale(1);
	}

	private List<String> priorityImprovements(JsonNode root) {
		JsonNode array = root.get("priority_improvements");
		if (array == null || !array.isArray()) return new ArrayList<>();
		List<String> result = new ArrayList<>();
		for (JsonNode item : array) {
			if (!item.isObject()) throw invalid();
			String problem = text(item, "problem");
			String improved = text(item, "improved_example");
			String explanation = text(item, "explanation_zh");
			result.add(problem + "；建议：" + improved + "。" + explanation);
		}
		return result;
	}

	private List<String> textArray(JsonNode node, String field) {
		JsonNode array = node.get(field);
		if (array == null || array.isNull()) return List.of();
		if (!array.isArray()) throw invalid();
		List<String> result = new ArrayList<>();
		for (JsonNode item : array) {
			if (!item.isTextual() || item.asString().isBlank()) throw invalid();
			result.add(item.asString().strip());
		}
		return result;
	}

	private JsonNode object(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || !value.isObject()) throw invalid();
		return value;
	}

	private String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || !value.isTextual() || value.asString().isBlank()) {
			throw invalid();
		}
		return value.asString().strip();
	}

	private EvaluationException invalid() {
		return new EvaluationException(
				EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
	}
}
