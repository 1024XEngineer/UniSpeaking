package com.unispeaking.domain.dto.evaluation;

import com.unispeaking.domain.vo.evaluation.InterviewDimension;
import java.math.BigDecimal;

/**
 * 单维度分数与建议。{@code score} 允许为 {@code null}（覆盖率降级：无有效语音时
 * 发音/流利维度 score 为 NULL 并带标注）。
 */
public record InterviewDimensionScore(
		InterviewDimension dimension,
		BigDecimal score,
		String evaluation,
		String advice) {
}
