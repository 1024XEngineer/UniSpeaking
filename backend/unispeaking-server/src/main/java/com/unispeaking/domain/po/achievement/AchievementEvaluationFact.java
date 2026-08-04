package com.unispeaking.domain.po.achievement;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AchievementEvaluationFact(
		String sessionId,
		OffsetDateTime createdAt,
		BigDecimal finalScore) {
}
