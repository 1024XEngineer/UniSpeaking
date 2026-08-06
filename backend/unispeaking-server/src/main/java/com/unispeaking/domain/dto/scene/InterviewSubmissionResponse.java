package com.unispeaking.domain.dto.scene;

import java.time.Instant;

public record InterviewSubmissionResponse(
		String submissionId,
		int questionNo,
		InterviewProcessingStatus processingStatus,
		boolean retryable,
		String errorCode,
		String message,
		Instant updatedAt) {
}
