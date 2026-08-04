package com.unispeaking.domain.po.feedback;

import com.unispeaking.domain.vo.feedback.FeedbackStatus;
import java.time.Instant;
import java.util.UUID;

public record UserFeedback(
		UUID id,
		String feedbackNo,
		UUID userId,
		String lookupCodeHash,
		String categoryId,
		String title,
		String description,
		String environment,
		FeedbackStatus status,
		String reply,
		Instant repliedAt,
		Instant createdAt,
		Instant updatedAt) {

	public UserFeedback withResolution(
			FeedbackStatus nextStatus,
			String nextReply,
			Instant changedAt) {
		return new UserFeedback(
				id,
				feedbackNo,
				userId,
				lookupCodeHash,
				categoryId,
				title,
				description,
				environment,
				nextStatus,
				nextReply,
				nextReply == null ? null : changedAt,
				createdAt,
				changedAt);
	}
}
