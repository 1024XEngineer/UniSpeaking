package com.unispeaking.domain.dto.profile;

import java.time.YearMonth;
import java.util.List;

public record LearningCalendarResponse(
		YearMonth yearMonth,
		List<LearningCalendarDayResponse> days) {

	public LearningCalendarResponse {
		days = List.copyOf(days);
	}
}
