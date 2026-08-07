package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.IeltsTopicType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record IeltsTopicSummaryResponse(
		String id,
		String title,
		IeltsTopicType topicType,
		String category,
		String categoryLabel,
		String source,
		long questionCount,
		int practiceCount,
		int mockTestCount,
		int randomPartPracticeCount,
		int selectedPartPracticeCount,
		String latestPracticeType,
		BigDecimal latestPerformanceScore,
		String latestPerformanceSummary,
		OffsetDateTime lastPracticedAt) {

	public IeltsTopicSummaryResponse(
			String id,
			String title,
			IeltsTopicType topicType,
			String category,
			String categoryLabel,
			String source,
			long questionCount) {
		this(
				id,
				title,
				topicType,
				category,
				categoryLabel,
				source,
				questionCount,
				0,
				0,
				0,
				0,
				null,
				null,
				null,
				null);
	}
}
