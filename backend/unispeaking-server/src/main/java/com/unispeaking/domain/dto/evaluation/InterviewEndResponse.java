package com.unispeaking.domain.dto.evaluation;

import com.unispeaking.domain.vo.evaluation.ReportStatus;

/**
 * 幂等结束编排响应：{@code reportStatus} 为该会话报告行当前生命周期状态
 * （PROCESSING/COMPLETED/FAILED），前端据此进入报告页轮询。
 */
public record InterviewEndResponse(
		String sessionId,
		ReportStatus reportStatus) {
}
