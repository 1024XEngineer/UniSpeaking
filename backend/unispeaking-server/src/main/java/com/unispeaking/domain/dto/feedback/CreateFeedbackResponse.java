package com.unispeaking.domain.dto.feedback;

public record CreateFeedbackResponse(
		FeedbackResponse feedback,
		String lookupCode) {
}
