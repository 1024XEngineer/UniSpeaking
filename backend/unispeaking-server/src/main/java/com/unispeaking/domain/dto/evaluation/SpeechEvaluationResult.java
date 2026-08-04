package com.unispeaking.domain.dto.evaluation;

import java.math.BigDecimal;

/**
 * 通用语音评分结果，不包含会话、转写或发音明细。
 */
public record SpeechEvaluationResult(
		BigDecimal accuracyScore,
		BigDecimal fluencyScore,
		int effectiveDurationUnits,
		int validPhonemeCount) {
}
