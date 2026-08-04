package com.unispeaking.domain.dto.feedback;

import com.unispeaking.domain.vo.feedback.FeedbackStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateFeedbackRequest(
		@NotNull(message = "不能为空")
		FeedbackStatus status,
		@Size(max = 4000, message = "不能超过 4000 个字符")
		String reply) {
}
