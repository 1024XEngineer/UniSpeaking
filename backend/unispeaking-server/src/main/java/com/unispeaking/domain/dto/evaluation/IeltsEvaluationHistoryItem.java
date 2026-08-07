package com.unispeaking.domain.dto.evaluation;

import com.unispeaking.domain.vo.scene.IeltsMode;
import com.unispeaking.domain.vo.scene.IeltsPart;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record IeltsEvaluationHistoryItem(
		String sessionId,
		String ieltsId,
		IeltsMode mode,
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
		List<String> recommendedExpressions,
		List<IeltsPartEvaluation> partEvaluations,
		String topicSelectionMethod,
		Map<IeltsPart, String> topicTitles,
		List<String> recordingUrls,
		Instant startedAt,
		Instant endedAt,
		String fluencyCoherenceReason,
		String lexicalResourceReason,
		String grammaticalRangeAccuracyReason,
		String pronunciationReason) {

	public IeltsEvaluationHistoryItem {
		strengths = strengths == null ? List.of() : List.copyOf(strengths);
		improvements = improvements == null
				? List.of()
				: List.copyOf(improvements);
		recommendedExpressions = recommendedExpressions == null
				? List.of()
				: List.copyOf(recommendedExpressions);
		partEvaluations = partEvaluations == null
				? List.of()
				: List.copyOf(partEvaluations);
		topicTitles = topicTitles == null
				? Map.of()
				: Map.copyOf(topicTitles);
		recordingUrls = recordingUrls == null
				? List.of()
				: List.copyOf(recordingUrls);
	}
}
