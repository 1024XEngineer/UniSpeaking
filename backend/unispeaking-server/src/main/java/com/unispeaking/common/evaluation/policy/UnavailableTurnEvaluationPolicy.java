package com.unispeaking.common.evaluation.policy;

import com.unispeaking.domain.dto.evaluation.DialogueTurnEvaluationResult;
import java.math.BigDecimal;
import java.util.List;

/**
 * Keeps a dialogue turn durable when an external scoring dependency is
 * temporarily unavailable. These records count toward scenario progress but
 * are excluded from aggregate speech scores.
 */
public final class UnavailableTurnEvaluationPolicy {

	public static final String FEEDBACK_SUMMARY = "本轮评分暂不可用，已保留对话内容";

	private UnavailableTurnEvaluationPolicy() {
	}

	public static DialogueTurnEvaluationResult createResult(
			int turnNo,
			String transcript) {
		return new DialogueTurnEvaluationResult(
				turnNo,
				transcript,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				FEEDBACK_SUMMARY,
				"",
				List.of());
	}

	public static boolean isUnavailable(CustomScores scores) {
		return scores != null
				&& isMissingOrZero(scores.overallScore())
				&& isMissingOrZero(scores.rhythmScore())
				&& isMissingOrZero(scores.toneScore())
				&& isMissingOrZero(scores.integrityScore())
				&& isMissingOrZero(scores.pronunciationScore())
				&& isMissingOrZero(scores.fluencyScore());
	}

	public record CustomScores(
			BigDecimal overallScore,
			BigDecimal rhythmScore,
			BigDecimal toneScore,
			BigDecimal integrityScore,
			BigDecimal pronunciationScore,
			BigDecimal fluencyScore,
			String feedbackSummary) {
	}

	private static boolean isMissingOrZero(BigDecimal score) {
		return score == null || score.compareTo(BigDecimal.ZERO) == 0;
	}
}
