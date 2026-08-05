package com.unispeaking.domain.po.scene;

import java.math.BigDecimal;
import java.util.UUID;

public record IeltsUserSettings(
		UUID userId,
		BigDecimal targetScore,
		int todayCompletedCount,
		String preferredVoice) {
}
