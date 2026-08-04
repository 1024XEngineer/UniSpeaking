package com.unispeaking.domain.dto.scene;

import java.util.List;

public record IeltsTopicSearchResponse(
		List<IeltsCategoryResponse> categories,
		List<IeltsTopicSummaryResponse> topics,
		int page,
		int pageSize,
		long total,
		int totalPages) {
}
