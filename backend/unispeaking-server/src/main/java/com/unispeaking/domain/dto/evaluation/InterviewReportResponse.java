package com.unispeaking.domain.dto.evaluation;

import com.unispeaking.domain.vo.evaluation.ReportStatus;

/**
 * 报告查询/重试响应。{@code status} 为 PROCESSING/COMPLETED/FAILED；
 * {@code report} 仅 COMPLETED 时非空；{@code failureReason} 仅 FAILED 时非空。
 */
public record InterviewReportResponse(
		String sessionId,
		String sceneId,
		ReportStatus status,
		InterviewReport report,
		String failureReason) {
}
