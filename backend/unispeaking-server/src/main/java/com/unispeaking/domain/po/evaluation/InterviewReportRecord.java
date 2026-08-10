package com.unispeaking.domain.po.evaluation;

import com.unispeaking.domain.vo.evaluation.ReportStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 面试报告持久化记录（PO ↔ Entity 双层转换的上层领域对象），行即报告任务。
 * {@code status} 承载异步生命周期；五维 score 允许 NULL（覆盖率降级）。
 */
public record InterviewReportRecord(
		String sessionId,
		String sceneId,
		String userId,
		ReportStatus status,
		BigDecimal overallScore,
		String summary,
		BigDecimal fluencyScore,
		String fluencyEvaluation,
		String fluencyAdvice,
		BigDecimal pronunciationIntelligibilityScore,
		String pronunciationIntelligibilityEvaluation,
		String pronunciationIntelligibilityAdvice,
		BigDecimal logicCoherenceScore,
		String logicCoherenceEvaluation,
		String logicCoherenceAdvice,
		BigDecimal grammarControlScore,
		String grammarControlEvaluation,
		String grammarControlAdvice,
		BigDecimal vocabularyExpressionScore,
		String vocabularyExpressionEvaluation,
		String vocabularyExpressionAdvice,
		int retryCount,
		String failureReason,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {
}
