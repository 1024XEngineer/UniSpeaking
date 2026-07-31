package com.unispeaking.common.evaluation.calculation;

import java.math.BigDecimal;

/**
 * 一条可评分气泡参与整场发音汇总的输入。
 *
 * @param accuracyScore 按音素时长和合格覆盖率计算的单轮准确度
 * @param fluencyScore 单轮流利度
 * @param audioNaturalnessScore 单轮声音自然度
 * @param effectiveDurationUnits 有效音素计权时长，单位为 10 ms
 * @param validPhonemeCount 有效音素数量
 */
public record TurnScoreContribution(
		BigDecimal accuracyScore,
		BigDecimal fluencyScore,
		BigDecimal audioNaturalnessScore,
		int effectiveDurationUnits,
		int validPhonemeCount) {
}
