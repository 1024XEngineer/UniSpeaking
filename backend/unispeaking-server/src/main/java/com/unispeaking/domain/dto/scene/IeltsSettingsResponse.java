package com.unispeaking.domain.dto.scene;

import java.math.BigDecimal;

public record IeltsSettingsResponse(
		BigDecimal targetScore,
		int todayCompletedCount,
		String examinerId,
		String preferredVoice,
		BigDecimal latestEstimatedScore) {
}
