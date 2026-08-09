package com.unispeaking.domain.dto.asset;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 面试学习资产摘要：场景快照（jobTitle/difficulty）+ 最近报告（status/score/time）+
 * 复练次数（{@code interview_report} 行数）。
 */
public record InterviewAssetItem(
		String sceneId,
		String jobTitle,
		String difficulty,
		String latestSessionId,
		String latestReportStatus,
		BigDecimal latestOverallScore,
		OffsetDateTime latestPracticedAt,
		int practiceCount,
		OffsetDateTime createdAt) {
}
