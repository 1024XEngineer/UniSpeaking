package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.vo.evaluation.ReportStatus;

/**
 * {@code submitTurn} 响应：本轮状态 + 报告状态。
 *
 * @param state        本轮状态快照，供前端决定是否停止录音并关闭实时连接
 * @param reportStatus 未结束时为 {@code null}；状态机判定结束时为 {@link ReportStatus#PROCESSING}
 */
public record InterviewTurnResult(
		InterviewTurnStateResponse state,
		ReportStatus reportStatus) {
}
