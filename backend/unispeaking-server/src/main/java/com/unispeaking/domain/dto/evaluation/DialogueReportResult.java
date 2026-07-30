package com.unispeaking.domain.dto.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 整场会话的五维评分、总分和文字反馈。
 *
 * <p>公开报告只包含 accuracy、fluency、grammar、vocabulary、
 * naturalness 五个维度及 finalScore，不再暴露旧的中间综合分或任务分。</p>
 */
public record DialogueReportResult(
		BigDecimal accuracyScore,
		BigDecimal fluencyScore,
		BigDecimal grammarScore,
		BigDecimal vocabularyScore,
		BigDecimal naturalnessScore,
		BigDecimal finalScore,
		String summary,
		List<String> strengths,
		List<String> improvements) {

	public DialogueReportResult {
		accuracyScore = require(accuracyScore, "accuracyScore");
		fluencyScore = require(fluencyScore, "fluencyScore");
		grammarScore = require(grammarScore, "grammarScore");
		vocabularyScore = require(vocabularyScore, "vocabularyScore");
		naturalnessScore = require(naturalnessScore, "naturalnessScore");
		finalScore = require(finalScore, "finalScore");
		summary = Objects.requireNonNull(summary, "summary must not be null");
		strengths = List.copyOf(Objects.requireNonNull(
				strengths,
				"strengths must not be null"));
		improvements = List.copyOf(Objects.requireNonNull(
				improvements,
				"improvements must not be null"));
	}

	private static BigDecimal require(BigDecimal value, String fieldName) {
		return Objects.requireNonNull(value, fieldName + " must not be null");
	}
}
