package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.TargetRoleSummary;

public record CreateInterviewResponse(
		String interviewId,
		String sessionId,
		InterviewDifficulty difficulty,
		InterviewRuntimeStatus status,
		TargetRoleSummary roleSummary,
		InterviewAiQuestionResponse firstQuestion) {
}
