package com.unispeaking.domain.po.scene;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record IeltsUserSettings(
		UUID userId,
		BigDecimal targetScore,
		int todayCompletedCount,
		String preferredVoice,
		int currentStreakDays,
		int totalCheckInDays,
		LocalDate lastCheckInDate) {

	public IeltsUserSettings(
			UUID userId,
			BigDecimal targetScore,
			int todayCompletedCount,
			String preferredVoice) {
		this(userId, targetScore, todayCompletedCount, preferredVoice, 0, 0, null);
	}
}
