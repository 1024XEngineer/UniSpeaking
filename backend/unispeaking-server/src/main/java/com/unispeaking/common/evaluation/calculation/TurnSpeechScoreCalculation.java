package com.unispeaking.common.evaluation.calculation;

import java.math.BigDecimal;

/**
 * 单轮语音评分及整场聚合所需的覆盖信息。
 */
public record TurnSpeechScoreCalculation(
		BigDecimal phonemeAverage,
		BigDecimal accuracyScore,
		BigDecimal fluencyScore,
		BigDecimal audioNaturalnessScore,
		int effectiveDurationUnits,
		int validPhonemeCount) {

	public TurnScoreContribution toContribution() {
		return new TurnScoreContribution(
				accuracyScore,
				fluencyScore,
				audioNaturalnessScore,
				effectiveDurationUnits,
				validPhonemeCount);
	}
}
