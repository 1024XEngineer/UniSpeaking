package com.unispeaking.domain.dto.asset;

import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import java.time.OffsetDateTime;
import java.util.Objects;

public record SessionEvaluationRecord(
		String sceneId,
		String sessionId,
		DialogueReportResult report,
		OffsetDateTime createdAt) {

	public SessionEvaluationRecord {
		Objects.requireNonNull(sceneId, "sceneId must not be null");
		Objects.requireNonNull(sessionId, "sessionId must not be null");
		Objects.requireNonNull(report, "report must not be null");
		Objects.requireNonNull(createdAt, "createdAt must not be null");
	}
}
