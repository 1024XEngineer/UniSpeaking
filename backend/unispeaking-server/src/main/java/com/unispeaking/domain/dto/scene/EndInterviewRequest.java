package com.unispeaking.domain.dto.scene;

import jakarta.validation.constraints.NotNull;

public record EndInterviewRequest(
		@NotNull Boolean confirmInsufficientData) implements InterviewApiRequest {
}
