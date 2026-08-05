package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.TargetRoleSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record InterviewDetailResponse(
		String interviewId,
		String jobTitle,
		InterviewDifficulty difficulty,
		TargetRoleSummary roleSummary,
		List<InterviewQuestionResponse> questions,
		InterviewReportResponse report,
		InterviewRecordingMetadataResponse recording,
		OffsetDateTime completedAt) {

	public InterviewDetailResponse {
		Objects.requireNonNull(roleSummary, "roleSummary");
		questions = questions == null ? List.of() : List.copyOf(questions);
		if (questions.isEmpty()) {
			throw new IllegalArgumentException("questions must not be empty");
		}
		Objects.requireNonNull(report, "report");
		Objects.requireNonNull(recording, "recording");
		Objects.requireNonNull(completedAt, "completedAt");
	}
}
