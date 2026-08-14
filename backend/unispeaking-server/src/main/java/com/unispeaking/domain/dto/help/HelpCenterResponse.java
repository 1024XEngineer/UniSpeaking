package com.unispeaking.domain.dto.help;

import java.util.List;

public record HelpCenterResponse(List<HelpCategoryResponse> categories) {

	public HelpCenterResponse {
		categories = List.copyOf(categories);
	}

	public record HelpCategoryResponse(
			String id,
			String title,
			String description,
			int articleCount) {
	}
}
