package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.InterviewReportType;

public record EndInterviewResponse(
		String interviewId,
		InterviewEndStatus processingStatus,
		InterviewReportType reportType,
		boolean confirmationRequired,
		int actualWords,
		int minimumWords) {
}
