package com.unispeaking.domain.dto.achievement;

import java.time.Instant;

public record AchievementAcknowledgeResponse(
		String achievementId,
		Instant acknowledgedAt) {
}
