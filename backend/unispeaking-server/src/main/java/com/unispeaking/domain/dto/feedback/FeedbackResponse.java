package com.unispeaking.domain.dto.feedback;

import com.unispeaking.domain.po.feedback.UserFeedback;
import com.unispeaking.domain.vo.feedback.FeedbackStatus;
import java.time.Instant;

public record FeedbackResponse(
		String feedbackNo,
		String categoryId,
		String title,
		String description,
		String environment,
		FeedbackStatus status,
		String reply,
		Instant repliedAt,
		Instant createdAt,
		Instant updatedAt) {

	public static FeedbackResponse from(UserFeedback feedback) {
		return new FeedbackResponse(
				feedback.feedbackNo(),
				feedback.categoryId(),
				feedback.title(),
				feedback.description(),
				feedback.environment(),
				feedback.status(),
				feedback.reply(),
				feedback.repliedAt(),
				feedback.createdAt(),
				feedback.updatedAt());
	}
}
