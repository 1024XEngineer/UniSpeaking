package com.unispeaking.domain.po.scene;

import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.RecommendedExpression;
import java.util.List;

public record IeltsQuestion(
		String id,
		String topicId,
		IeltsPart part,
		int sortNo,
		String questionText,
		List<String> cuePoints,
		List<RecommendedExpression> recommendedExpressions) {

	public IeltsQuestion {
		cuePoints = cuePoints == null ? List.of() : List.copyOf(cuePoints);
		recommendedExpressions = recommendedExpressions == null
				? List.of()
				: List.copyOf(recommendedExpressions);
	}
}
