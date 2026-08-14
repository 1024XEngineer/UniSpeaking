package com.unispeaking.domain.dto.help;

public record HelpArticleResponse(
		String id,
		String categoryId,
		String title,
		String summary,
		String updatedAt) {
}
