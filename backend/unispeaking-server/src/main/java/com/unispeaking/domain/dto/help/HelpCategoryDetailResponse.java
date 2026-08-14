package com.unispeaking.domain.dto.help;

import java.util.List;

public record HelpCategoryDetailResponse(
		String id,
		String title,
		String description,
		List<HelpArticleSummaryResponse> articles) {

	public HelpCategoryDetailResponse {
		articles = List.copyOf(articles);
	}
}
