package com.unispeaking.domain.dto.evaluation;

import com.unispeaking.domain.vo.scene.IeltsPart;
import java.math.BigDecimal;
import java.util.List;

public record IeltsEvaluationResult(
		IeltsPart part,
		String assessmentType,
		BigDecimal overallBandScore,
		BigDecimal fluencyCoherenceScore,
		BigDecimal lexicalResourceScore,
		BigDecimal grammaticalRangeAccuracyScore,
		BigDecimal pronunciationScore,
		String summary,
		List<String> strengths,
		List<String> improvements,
		List<IeltsPartEvaluation> partEvaluations,
		List<String> recommendedExpressions,
		String fluencyCoherenceReason,
		String lexicalResourceReason,
		String grammaticalRangeAccuracyReason,
		String pronunciationReason) {

	public IeltsEvaluationResult {
		strengths = strengths == null ? List.of() : List.copyOf(strengths);
		improvements = improvements == null
				? List.of()
				: List.copyOf(improvements);
		partEvaluations = partEvaluations == null
				? List.of()
				: List.copyOf(partEvaluations);
		recommendedExpressions = recommendedExpressions == null
				? List.of()
				: List.copyOf(recommendedExpressions);
	}

	public IeltsEvaluationResult(
			IeltsPart part,
			String assessmentType,
			BigDecimal overallBandScore,
			BigDecimal fluencyCoherenceScore,
			BigDecimal lexicalResourceScore,
			BigDecimal grammaticalRangeAccuracyScore,
			BigDecimal pronunciationScore,
			String summary,
			List<String> strengths,
			List<String> improvements,
			List<IeltsPartEvaluation> partEvaluations,
			List<String> recommendedExpressions) {
		this(
				part,
				assessmentType,
				overallBandScore,
				fluencyCoherenceScore,
				lexicalResourceScore,
				grammaticalRangeAccuracyScore,
				pronunciationScore,
				summary,
				strengths,
				improvements,
				partEvaluations,
				recommendedExpressions,
				null,
				null,
				null,
				null);
	}

	public IeltsEvaluationResult(
			IeltsPart part,
			String assessmentType,
			BigDecimal overallBandScore,
			BigDecimal fluencyCoherenceScore,
			BigDecimal lexicalResourceScore,
			BigDecimal grammaticalRangeAccuracyScore,
			BigDecimal pronunciationScore,
			String summary,
			List<String> strengths,
			List<String> improvements) {
		this(
				part,
				assessmentType,
				overallBandScore,
				fluencyCoherenceScore,
				lexicalResourceScore,
				grammaticalRangeAccuracyScore,
				pronunciationScore,
				summary,
				strengths,
				improvements,
				List.of(),
				List.of(),
				null,
				null,
				null,
				null);
	}
}
