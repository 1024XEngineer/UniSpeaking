package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.IeltsTopicType;

public record IeltsTopicSummaryResponse(
		String id,
		String title,
		IeltsTopicType topicType,
		String category,
		String categoryLabel,
		String source,
		long questionCount) {
}
