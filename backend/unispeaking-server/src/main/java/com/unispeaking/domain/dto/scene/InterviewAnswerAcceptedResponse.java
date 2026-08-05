package com.unispeaking.domain.dto.scene;

public record InterviewAnswerAcceptedResponse(
		InterviewSubmissionResponse submission,
		String statePath) {
}
