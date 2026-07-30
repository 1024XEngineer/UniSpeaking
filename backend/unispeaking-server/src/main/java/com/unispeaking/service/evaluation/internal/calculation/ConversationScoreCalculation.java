package com.unispeaking.service.evaluation.internal.calculation;

import java.math.BigDecimal;

/**
 * 整场报告的纯数值计算结果。
 *
 * <p>只承载五维评分与总分，所有分数均使用 HALF_UP 保留一位小数。</p>
 *
 * @param accuracyScore 单轮发音准确度的整场加权分
 * @param fluencyScore 单轮流利度的整场加权分
 * @param grammarScore 语法基础分
 * @param vocabularyScore 词汇基础分
 * @param naturalnessScore 自然度基础分
 * @param finalScore 五维加权总分
 */
public record ConversationScoreCalculation(
		BigDecimal accuracyScore,
		BigDecimal fluencyScore,
		BigDecimal grammarScore,
		BigDecimal vocabularyScore,
		BigDecimal naturalnessScore,
		BigDecimal finalScore) {
}
