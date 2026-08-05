package com.unispeaking.domain.dto.evaluation;

import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record IeltsEvaluationHistoryItem(
		String sessionId,
		String ieltsId,
		IeltsMode mode,
		IeltsPart part,
		String assessmentType,
		BigDecimal overallBandScore,
		BigDecimal fluencyCoherenceScore,
		String summary,
		List<String> strengths,
		List<String> improvements,
		Instant startedAt,
		Instant endedAt) {

	public IeltsEvaluationHistoryItem {
		strengths = strengths == null ? List.of() : List.copyOf(strengths);
		improvements = improvements == null
				? List.of()
				: List.copyOf(improvements);
	}
}
