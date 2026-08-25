package com.unispeaking.domain.dto.evaluation;

import com.unispeaking.domain.vo.task.AsyncTaskStatus;

public record IeltsEvaluationTaskResponse(
		String ieltsId,
		String sessionId,
		AsyncTaskStatus status,
		IeltsEvaluationResult result,
		String failureReason) {
}
