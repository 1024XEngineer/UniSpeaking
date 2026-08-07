package com.unispeaking.domain.dto.scene;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IeltsSettingsResponse(
		BigDecimal targetScore,
		int todayCompletedCount,
		String examinerId,
		String preferredVoice,
		BigDecimal latestEstimatedScore,
		int currentStreakDays,
		int totalCheckInDays,
		LocalDate lastCheckInDate) {

	public IeltsSettingsResponse(
			BigDecimal targetScore,
			int todayCompletedCount,
			String examinerId,
			String preferredVoice,
			BigDecimal latestEstimatedScore) {
		this(
				targetScore,
				todayCompletedCount,
				examinerId,
				preferredVoice,
				latestEstimatedScore,
				0,
				0,
				null);
	}
}
