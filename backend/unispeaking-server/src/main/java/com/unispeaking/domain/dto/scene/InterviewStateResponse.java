package com.unispeaking.domain.dto.scene;

import java.time.Instant;

public record InterviewStateResponse(
		String interviewId,
		String sessionId,
		InterviewRuntimeStatus status,
		String errorCode,
		String message,
		boolean retryable,
		int currentQuestionNo,
		boolean acceptingSubmissions,
		boolean endRequested,
		boolean confirmationRequired,
		int actualWords,
		int minimumWords,
		Instant lastSeen,
		InterviewSubmissionResponse latestSubmission,
		InterviewAiQuestionResponse nextQuestion) {
}
