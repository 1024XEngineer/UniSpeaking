package com.unispeaking.domain.dto.profile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProfileOverviewResponse(
		Account account,
		PracticeStatistics statistics,
		Calendar calendar) {
	public record Account(
			UUID userId,
			String email,
			String nickname,
			String displayName,
			String avatarUrl,
			Instant avatarUrlExpiresAt) {
	}

	public record Calendar(
			String month,
			List<LocalDate> checkedDates,
			boolean checkedInToday) {
		public Calendar {
			checkedDates = List.copyOf(checkedDates);
		}
	}

	public record PracticeStatistics(
			long weeklyPracticeSeconds,
			long trainingRecordCount,
			int consecutiveLearningDays,
			List<DailyPractice> lastSevenDays) {
		public PracticeStatistics {
			lastSevenDays = List.copyOf(lastSevenDays);
		}
	}

	public record DailyPractice(LocalDate date, long practiceSeconds) {
	}
}
