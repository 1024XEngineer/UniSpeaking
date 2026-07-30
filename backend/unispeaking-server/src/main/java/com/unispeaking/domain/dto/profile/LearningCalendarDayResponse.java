package com.unispeaking.domain.dto.profile;

import java.time.LocalDate;

public record LearningCalendarDayResponse(
		LocalDate date,
		long learningMinutes,
		int practiceCount,
		long savedAssetCount) {
}
