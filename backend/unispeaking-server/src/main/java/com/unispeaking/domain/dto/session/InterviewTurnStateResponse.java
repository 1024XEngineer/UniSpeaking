package com.unispeaking.domain.dto.session;

/**
 * 本轮状态快照：{@code shouldEnd=true} 时前端负责停止录音并关闭实时连接（后端不主动断开）。
 */
public record InterviewTurnStateResponse(
		boolean shouldEnd,
		int completedTopicCount,
		int coveredTopicCount,
		String currentTopic) {
}
