package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.InterviewReportType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

public record InterviewHistoryItemResponse(
		String interviewId,
		String jobTitle,
		InterviewDifficulty difficulty,
		InterviewReportType reportType,
		BigDecimal overallScore,
		int recordingDurationSeconds,
		OffsetDateTime completedAt) {

	public InterviewHistoryItemResponse {
		if (interviewId == null || interviewId.isBlank()) {
			throw new IllegalArgumentException("interviewId must not be blank");
		}
		if (jobTitle == null || jobTitle.isBlank()) {
			throw new IllegalArgumentException("jobTitle must not be blank");
		}
		Objects.requireNonNull(difficulty, "difficulty");
		Objects.requireNonNull(reportType, "reportType");
		Objects.requireNonNull(overallScore, "overallScore");
		if (recordingDurationSeconds < 0) {
			throw new IllegalArgumentException(
					"recordingDurationSeconds must not be negative");
		}
		Objects.requireNonNull(completedAt, "completedAt");
	}
}
