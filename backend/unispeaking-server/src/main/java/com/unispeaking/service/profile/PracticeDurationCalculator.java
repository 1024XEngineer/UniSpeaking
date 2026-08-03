package com.unispeaking.service.profile;

import com.unispeaking.domain.dto.profile.ProfileOverviewResponse;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

public class PracticeDurationCalculator {

	public static final long MINIMUM_PRACTICE_SECONDS = 30;

	public ProfileOverviewResponse.PracticeStatistics calculate(
			List<PracticeSessionRecord> records,
			LocalDate today,
			Instant now,
			ZoneId zoneId,
			long trainingRecordCount,
			int consecutiveLearningDays) {
		Instant weekStart = today
				.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
				.atStartOfDay(zoneId)
				.toInstant();
		long weeklySeconds = eligible(records).stream()
				.mapToLong(record -> overlapSeconds(record, weekStart, now))
				.sum();
		List<ProfileOverviewResponse.DailyPractice> dailyPractice =
				java.util.stream.IntStream.rangeClosed(0, 6)
						.mapToObj(offset -> today.minusDays(6L - offset))
						.map(date -> new ProfileOverviewResponse.DailyPractice(
								date,
								practiceSecondsForDate(records, date, zoneId, now)))
						.toList();
		return new ProfileOverviewResponse.PracticeStatistics(
				weeklySeconds,
				trainingRecordCount,
				consecutiveLearningDays,
				dailyPractice);
	}

	private long practiceSecondsForDate(
			List<PracticeSessionRecord> records,
			LocalDate date,
			ZoneId zoneId,
			Instant now) {
		Instant start = date.atStartOfDay(zoneId).toInstant();
		Instant nextDay = date.plusDays(1).atStartOfDay(zoneId).toInstant();
		Instant end = nextDay.isBefore(now) ? nextDay : now;
		if (!end.isAfter(start)) {
			return 0;
		}
		return eligible(records).stream()
				.mapToLong(record -> overlapSeconds(record, start, end))
				.sum();
	}

	private List<PracticeSessionRecord> eligible(
			List<PracticeSessionRecord> records) {
		if (records == null || records.isEmpty()) {
			return List.of();
		}
		return records.stream()
				.filter(record -> record.startedAt() != null && record.endedAt() != null)
				.filter(record -> !record.endedAt().isBefore(record.startedAt()))
				.filter(record -> Duration.between(
						record.startedAt(), record.endedAt()).getSeconds()
						>= MINIMUM_PRACTICE_SECONDS)
				.toList();
	}

	private long overlapSeconds(
			PracticeSessionRecord record,
			Instant rangeStart,
			Instant rangeEnd) {
		Instant start = record.startedAt().isAfter(rangeStart)
				? record.startedAt()
				: rangeStart;
		Instant end = record.endedAt().isBefore(rangeEnd)
				? record.endedAt()
				: rangeEnd;
		return end.isAfter(start) ? Duration.between(start, end).getSeconds() : 0;
	}
}
