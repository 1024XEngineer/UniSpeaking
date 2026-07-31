package com.unispeaking.common.evaluation.calculation;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 按会话五维评分 v1.0 计算单轮 Accuracy、Fluency 和声音自然度。
 */
public final class TurnSpeechScoreCalculator {

	private static final BigDecimal PASSING_PHONEME_SCORE = new BigDecimal("60");
	private static final BigDecimal HUNDRED = new BigDecimal("100");
	private static final int MIN_PHONEME_WEIGHT = 2;
	private static final int MAX_PHONEME_WEIGHT = 30;

	private TurnSpeechScoreCalculator() {
	}

	public static TurnSpeechScoreCalculation calculate(
			PronunciationAssessmentResult assessment) {
		if (assessment == null || assessment.words() == null) {
			throw incomplete();
		}

		BigDecimal weightedScore = BigDecimal.ZERO;
		BigDecimal passedWeight = BigDecimal.ZERO;
		int totalWeight = 0;
		int phonemeCount = 0;
		for (var word : assessment.words()) {
			if (word == null || word.phonemes() == null) {
				continue;
			}
			for (PronunciationPhonemeResult phoneme : word.phonemes()) {
				if (phoneme == null
						|| phoneme.pronunciationScore() == null) {
					continue;
				}
				if (!validScore(phoneme.pronunciationScore())) {
					throw incomplete();
				}
				if (phoneme.startPosition() < 0
						|| phoneme.endPosition() <= phoneme.startPosition()) {
					continue;
				}
				int duration = phoneme.endPosition() - phoneme.startPosition();
				int weight = Math.max(
						MIN_PHONEME_WEIGHT,
						Math.min(duration, MAX_PHONEME_WEIGHT));
				BigDecimal decimalWeight = BigDecimal.valueOf(weight);
				weightedScore = weightedScore.add(
						phoneme.pronunciationScore().multiply(decimalWeight));
				if (phoneme.pronunciationScore().compareTo(PASSING_PHONEME_SCORE) >= 0) {
					passedWeight = passedWeight.add(decimalWeight);
				}
				totalWeight += weight;
				phonemeCount++;
			}
		}
		if (totalWeight == 0 || phonemeCount == 0) {
			throw incomplete();
		}

		BigDecimal total = BigDecimal.valueOf(totalWeight);
		BigDecimal phonemeAverage = divide(weightedScore, total);
		BigDecimal coverage = divide(passedWeight.multiply(HUNDRED), total);
		BigDecimal accuracy = round(
				phonemeAverage.multiply(new BigDecimal("0.80"))
						.add(coverage.multiply(new BigDecimal("0.20"))));
		BigDecimal fluency = round(
				requireScore(assessment.fluencyScore()).multiply(new BigDecimal("0.65"))
						.add(requireScore(assessment.rhythmScore())
								.multiply(new BigDecimal("0.25")))
						.add(phonemeAverage.multiply(new BigDecimal("0.10"))));
		BigDecimal naturalness = weightedAvailableScore(
				new WeightedScore(
						assessment.rhythmScore(),
						new BigDecimal("0.40")),
				new WeightedScore(
						assessment.fluencyScore(),
						new BigDecimal("0.25")),
				new WeightedScore(
						assessment.toneScore(),
						new BigDecimal("0.20")),
				new WeightedScore(
						phonemeAverage,
						new BigDecimal("0.15")));

		return new TurnSpeechScoreCalculation(
				phonemeAverage,
				accuracy,
				fluency,
				naturalness,
				totalWeight,
				phonemeCount);
	}

	private static BigDecimal requireScore(BigDecimal score) {
		if (!validScore(score)) {
			throw incomplete();
		}
		return score;
	}

	private static boolean validScore(BigDecimal score) {
		return score != null
				&& score.compareTo(BigDecimal.ZERO) >= 0
				&& score.compareTo(HUNDRED) <= 0;
	}

	private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
		return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
	}

	private static BigDecimal round(BigDecimal value) {
		return value.setScale(1, RoundingMode.HALF_UP);
	}

	private static BigDecimal weightedAvailableScore(
			WeightedScore... scores) {
		BigDecimal weightedTotal = BigDecimal.ZERO;
		BigDecimal availableWeight = BigDecimal.ZERO;
		for (WeightedScore score : scores) {
			if (score.value() == null) {
				continue;
			}
			BigDecimal validValue = requireScore(score.value());
			weightedTotal = weightedTotal.add(
					validValue.multiply(score.weight()));
			availableWeight = availableWeight.add(score.weight());
		}
		if (availableWeight.signum() == 0) {
			throw incomplete();
		}
		return round(divide(weightedTotal, availableWeight));
	}

	private record WeightedScore(
			BigDecimal value,
			BigDecimal weight) {
	}

	private static EvaluationException incomplete() {
		return new EvaluationException(EvaluationErrorCode.PROVIDER_RESPONSE_INCOMPLETE);
	}
}
