package com.unispeaking.domain.po.scene;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record IeltsTopicPracticeSummary(
		String topicId,
		int practiceCount,
		int mockTestCount,
		int randomPartPracticeCount,
		int selectedPartPracticeCount,
		String latestPracticeType,
		BigDecimal latestPerformanceScore,
		String latestPerformanceSummary,
		OffsetDateTime lastPracticedAt) {
}
