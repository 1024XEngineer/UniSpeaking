package com.unispeaking.domain.dto.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 面试报告只读模型（COMPLETED 时返回）：{@code overallScore} 为整场 LLM 综合判断，
 * 五维见 {@code dimensions}；{@code completedAt} 投影自 COMPLETED 行的 {@code updated_at}。
 */
public record InterviewReport(
		String sessionId,
		String sceneId,
		BigDecimal overallScore,
		String summary,
		List<InterviewDimensionScore> dimensions,
		Instant completedAt) {
}
