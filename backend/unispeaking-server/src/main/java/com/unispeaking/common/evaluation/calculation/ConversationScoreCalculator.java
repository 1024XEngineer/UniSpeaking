package com.unispeaking.common.evaluation.calculation;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.evaluation.model.ConversationLanguageAssessment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;

/**
 * 按会话五维评分 v1.0 计算整场分数。
 */
public final class ConversationScoreCalculator {

	private static final int SCORE_SCALE = 1;
	private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
	private static final BigDecimal MAX_SCORE = new BigDecimal("100");
	private static final int MAX_TURN_WEIGHT = 3_000;

	private static final BigDecimal ACCURACY_WEIGHT = new BigDecimal("0.25");
	private static final BigDecimal FLUENCY_WEIGHT = new BigDecimal("0.20");
	private static final BigDecimal GRAMMAR_WEIGHT = new BigDecimal("0.20");
	private static final BigDecimal VOCABULARY_WEIGHT = new BigDecimal("0.15");
	private static final BigDecimal NATURALNESS_WEIGHT = new BigDecimal("0.20");
	private static final BigDecimal AUDIO_NATURALNESS_WEIGHT = new BigDecimal("0.60");
	private static final BigDecimal TEXT_NATURALNESS_WEIGHT = new BigDecimal("0.40");

	private ConversationScoreCalculator() {
	}

	public static ConversationScoreCalculation calculate(
			List<TurnScoreContribution> turns,
			ConversationLanguageAssessment languageAssessment) {
		List<TurnScoreContribution> validTurns = requireTurns(turns);
		ConversationLanguageAssessment language =
				requireLanguageAssessment(languageAssessment);

		BigDecimal accuracy = aggregate(validTurns, TurnScoreContribution::accuracyScore);
		BigDecimal fluency = aggregate(validTurns, TurnScoreContribution::fluencyScore);
		BigDecimal audioNaturalness = aggregate(
				validTurns,
				TurnScoreContribution::audioNaturalnessScore);
		BigDecimal grammar = normalize(language.grammarScore());
		BigDecimal vocabulary = normalize(language.vocabularyScore());
		BigDecimal textNaturalness = normalize(language.textNaturalnessScore());
		BigDecimal naturalness = round(
				audioNaturalness.multiply(AUDIO_NATURALNESS_WEIGHT)
						.add(textNaturalness.multiply(TEXT_NATURALNESS_WEIGHT)));
		BigDecimal finalScore = round(
				accuracy.multiply(ACCURACY_WEIGHT)
						.add(fluency.multiply(FLUENCY_WEIGHT))
						.add(grammar.multiply(GRAMMAR_WEIGHT))
						.add(vocabulary.multiply(VOCABULARY_WEIGHT))
						.add(naturalness.multiply(NATURALNESS_WEIGHT)));

		return new ConversationScoreCalculation(
				accuracy,
				fluency,
				grammar,
				vocabulary,
				naturalness,
				finalScore);
	}

	private static List<TurnScoreContribution> requireTurns(
			List<TurnScoreContribution> turns) {
		if (turns == null) {
			throw new EvaluationException(EvaluationErrorCode.RESULT_INCOMPLETE);
		}
		if (turns.isEmpty()) {
			throw new EvaluationException(EvaluationErrorCode.NO_SCORABLE_UTTERANCES);
		}
		for (TurnScoreContribution turn : turns) {
			if (turn == null
					|| !valid(turn.accuracyScore())
					|| !valid(turn.fluencyScore())
					|| !valid(turn.audioNaturalnessScore())
					|| turn.effectiveDurationUnits() <= 0
					|| turn.validPhonemeCount() <= 0) {
				throw new EvaluationException(EvaluationErrorCode.RESULT_INCOMPLETE);
			}
		}
		return turns;
	}

	private static ConversationLanguageAssessment requireLanguageAssessment(
			ConversationLanguageAssessment assessment) {
		if (assessment == null
				|| assessment.grammarScore() == null
				|| assessment.vocabularyScore() == null
				|| assessment.textNaturalnessScore() == null) {
			throw new EvaluationException(
					EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
		}
		return assessment;
	}

	private static BigDecimal aggregate(
			List<TurnScoreContribution> turns,
			Function<TurnScoreContribution, BigDecimal> extractor) {
		BigDecimal weightedSum = BigDecimal.ZERO;
		BigDecimal totalWeight = BigDecimal.ZERO;
		for (TurnScoreContribution turn : turns) {
			BigDecimal weight = BigDecimal.valueOf(
					Math.min(turn.effectiveDurationUnits(), MAX_TURN_WEIGHT));
			weightedSum = weightedSum.add(extractor.apply(turn).multiply(weight));
			totalWeight = totalWeight.add(weight);
		}
		return round(weightedSum.divide(totalWeight, 8, ROUNDING));
	}

	private static BigDecimal normalize(BigDecimal score) {
		if (!valid(score)) {
			throw new EvaluationException(
					EvaluationErrorCode.PROVIDER_RESPONSE_INVALID);
		}
		return round(score);
	}

	private static boolean valid(BigDecimal score) {
		return score != null
				&& score.compareTo(BigDecimal.ZERO) >= 0
				&& score.compareTo(MAX_SCORE) <= 0;
	}

	private static BigDecimal round(BigDecimal score) {
		return score.setScale(SCORE_SCALE, ROUNDING);
	}
}
