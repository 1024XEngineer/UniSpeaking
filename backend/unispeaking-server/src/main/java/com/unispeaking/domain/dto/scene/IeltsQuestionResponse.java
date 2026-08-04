package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.RecommendedExpression;
import java.util.List;

public record IeltsQuestionResponse(
		String id,
		IeltsPart part,
		int sortNo,
		String questionText,
		List<String> cuePoints,
		List<RecommendedExpression> recommendedExpressions) {
}
