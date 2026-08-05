package com.unispeaking.common.evaluation.model;

import com.unispeaking.domain.vo.scene.IeltsPart;
import java.math.BigDecimal;
import java.util.List;

public record IeltsTextAssessment(
		IeltsPart part,
		BigDecimal fluencyCoherenceBand,
		BigDecimal lexicalResourceBand,
		BigDecimal grammaticalRangeAccuracyBand,
		String summary,
		List<String> strengths,
		List<String> improvements,
		String confidence) {

	public IeltsTextAssessment {
		strengths = List.copyOf(strengths);
		improvements = List.copyOf(improvements);
	}
}
