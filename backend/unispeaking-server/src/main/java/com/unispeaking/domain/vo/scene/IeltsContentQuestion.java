package com.unispeaking.domain.vo.scene;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record IeltsContentQuestion(
		String question,
		@JsonProperty("cue_points") List<String> cuePoints,
		@JsonProperty("recommended_expressions")
		List<RecommendedExpression> recommendedExpressions) {

	public IeltsContentQuestion {
		cuePoints = cuePoints == null ? List.of() : List.copyOf(cuePoints);
		recommendedExpressions = recommendedExpressions == null
				? List.of()
				: List.copyOf(recommendedExpressions);
	}
}
