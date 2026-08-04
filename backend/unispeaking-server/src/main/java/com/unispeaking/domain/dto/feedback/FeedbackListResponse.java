package com.unispeaking.domain.dto.feedback;

import java.util.List;

public record FeedbackListResponse(List<FeedbackResponse> feedbacks) {
	public FeedbackListResponse {
		feedbacks = List.copyOf(feedbacks);
	}
}
