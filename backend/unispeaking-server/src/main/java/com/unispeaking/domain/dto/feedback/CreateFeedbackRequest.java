package com.unispeaking.domain.dto.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFeedbackRequest(
		@NotBlank(message = "不能为空")
		@Pattern(
				regexp = "quick-start|account-login|ai-training|audio|learning-records|membership|privacy-security|feedback",
				message = "不在支持范围内")
		String categoryId,
		@NotBlank(message = "不能为空")
		@Size(max = 80, message = "不能超过 80 个字符")
		String title,
		@NotBlank(message = "不能为空")
		@Size(max = 2000, message = "不能超过 2000 个字符")
		String description,
		@Size(max = 200, message = "不能超过 200 个字符")
		String environment) {
}
