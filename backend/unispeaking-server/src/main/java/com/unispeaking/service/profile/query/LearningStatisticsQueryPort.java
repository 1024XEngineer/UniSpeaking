package com.unispeaking.service.profile.query;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public interface LearningStatisticsQueryPort {

	LearningSummary summary(UUID userId, ZoneId zoneId);

	List<LearningCalendarDay> calendar(
			UUID userId,
			YearMonth yearMonth,
			ZoneId zoneId);

	record LearningSummary(long weeklyMinutes, int continuousLearningDays) {
	}

	record LearningCalendarDay(
			LocalDate date,
			long learningMinutes,
			int practiceCount) {
	}
}
