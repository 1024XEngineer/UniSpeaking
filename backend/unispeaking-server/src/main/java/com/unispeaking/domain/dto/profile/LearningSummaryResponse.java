package com.unispeaking.domain.dto.profile;

public record LearningSummaryResponse(
		long weeklyMinutes,
		long savedAssetCount,
		int continuousLearningDays) {
}
