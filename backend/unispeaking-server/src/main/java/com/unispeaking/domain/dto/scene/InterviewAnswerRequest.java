package com.unispeaking.domain.dto.scene;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InterviewAnswerRequest(
		@NotBlank @Size(max = 64) String submissionId,
		@Min(1) int questionNo) implements InterviewApiRequest {
}
