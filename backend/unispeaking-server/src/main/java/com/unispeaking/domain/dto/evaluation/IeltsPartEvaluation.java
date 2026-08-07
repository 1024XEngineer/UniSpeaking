package com.unispeaking.domain.dto.evaluation;

import com.unispeaking.domain.vo.scene.IeltsPart;
import java.math.BigDecimal;
import java.util.List;

public record IeltsPartEvaluation(
		IeltsPart part,
		BigDecimal fluencyCoherenceScore,
		BigDecimal lexicalResourceScore,
		BigDecimal grammaticalRangeAccuracyScore,
		BigDecimal pronunciationScore,
		String summary,
		List<String> strengths,
		List<String> improvements,
		List<String> recommendedExpressions,
		String fluencyCoherenceReason,
		String lexicalResourceReason,
		String grammaticalRangeAccuracyReason,
		String pronunciationReason) {

	public IeltsPartEvaluation {
		strengths = strengths == null ? List.of() : List.copyOf(strengths);
		improvements = improvements == null ? List.of() : List.copyOf(improvements);
		recommendedExpressions = recommendedExpressions == null
				? List.of()
				: List.copyOf(recommendedExpressions);
	}

	public IeltsPartEvaluation(
			IeltsPart part,
			BigDecimal fluencyCoherenceScore,
			BigDecimal lexicalResourceScore,
			BigDecimal grammaticalRangeAccuracyScore,
			BigDecimal pronunciationScore,
			String summary,
			List<String> strengths,
			List<String> improvements,
			List<String> recommendedExpressions) {
		this(
				part,
				fluencyCoherenceScore,
				lexicalResourceScore,
				grammaticalRangeAccuracyScore,
				pronunciationScore,
				summary,
				strengths,
				improvements,
				recommendedExpressions,
				null,
				null,
				null,
				null);
	}
}
