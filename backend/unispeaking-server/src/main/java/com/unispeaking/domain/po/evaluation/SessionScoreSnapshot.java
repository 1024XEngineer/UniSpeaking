package com.unispeaking.domain.po.evaluation;

import java.math.BigDecimal;
import java.util.Objects;

public record SessionScoreSnapshot(
		String sessionId,
		BigDecimal accuracy,
		BigDecimal fluency,
		BigDecimal grammar,
		BigDecimal vocabulary,
		BigDecimal naturalness) {

	public SessionScoreSnapshot {
		Objects.requireNonNull(sessionId, "sessionId must not be null");
		Objects.requireNonNull(accuracy, "accuracy must not be null");
		Objects.requireNonNull(fluency, "fluency must not be null");
		Objects.requireNonNull(grammar, "grammar must not be null");
		Objects.requireNonNull(vocabulary, "vocabulary must not be null");
		Objects.requireNonNull(naturalness, "naturalness must not be null");
	}
}
