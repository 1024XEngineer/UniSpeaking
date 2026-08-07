package com.unispeaking.component.profile;

import com.unispeaking.domain.po.profile.WeeklyLearningGoals;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WeeklyGoalProgressCalculator {

	private static final Set<SceneType> INCLUDED_TYPES =
			EnumSet.of(SceneType.FREE_CHAT, SceneType.CUSTOM_SCENE);

	public WeeklyGoalProgress calculate(
			List<PracticeSessionRecord> records,
			WeeklyLearningGoals goals,
			Instant now,
			ZoneId zoneId) {
		Instant weekStart = now.atZone(zoneId)
				.toLocalDate()
				.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
				.atStartOfDay(zoneId)
				.toInstant();
		Instant weekEnd = weekStart.atZone(zoneId)
				.toLocalDate()
				.plusWeeks(1)
				.atStartOfDay(zoneId)
				.toInstant();
		List<PracticeSessionRecord> eligible = eligible(records);
		Map<SceneType, Long> durationByType = new EnumMap<>(SceneType.class);
		eligible.forEach(record -> {
			long seconds = overlapSeconds(record, weekStart, now);
			if (seconds > 0) {
				durationByType.merge(record.sceneType(), seconds, Long::sum);
			}
		});
		long completedDurationSeconds = durationByType.values().stream()
				.mapToLong(Long::longValue)
				.sum();
		List<TrainingTypeDuration> trainingTypeDurations = durationByType.entrySet()
				.stream()
				.map(entry -> new TrainingTypeDuration(
						entry.getKey(),
						entry.getValue(),
						progress(entry.getValue(), completedDurationSeconds)))
				.toList();
		long completedTrainingCount = eligible.stream()
				.filter(record -> !record.endedAt().isBefore(weekStart))
				.filter(record -> record.endedAt().isBefore(weekEnd))
				.filter(record -> !record.endedAt().isAfter(now))
				.count();
		long durationTargetSeconds = goals.durationTargetMinutes() * 60L;
		return new WeeklyGoalProgress(
				weekStart,
				weekEnd,
				completedDurationSeconds,
				Math.max(0, durationTargetSeconds - completedDurationSeconds),
				progress(completedDurationSeconds, durationTargetSeconds),
				completedDurationSeconds >= durationTargetSeconds,
				completedTrainingCount,
				Math.max(0, goals.trainingCountTarget() - completedTrainingCount),
				progress(completedTrainingCount, goals.trainingCountTarget()),
				completedTrainingCount >= goals.trainingCountTarget(),
				trainingTypeDurations);
	}

	private List<PracticeSessionRecord> eligible(
			List<PracticeSessionRecord> records) {
		if (records == null || records.isEmpty()) {
			return List.of();
		}
		return records.stream()
				.filter(record -> record.status() == SessionStatus.COMPLETED)
				.filter(record -> INCLUDED_TYPES.contains(record.sceneType()))
				.filter(record -> record.startedAt() != null && record.endedAt() != null)
				.filter(record -> !record.endedAt().isBefore(record.startedAt()))
				.filter(record -> Duration.between(
						record.startedAt(), record.endedAt()).getSeconds()
						>= PracticeDurationCalculator.MINIMUM_PRACTICE_SECONDS)
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

	private double progress(long completed, long target) {
		if (target <= 0) {
			return 0.0;
		}
		double percentage = Math.min(100.0, completed * 100.0 / target);
		return Math.round(percentage * 10.0) / 10.0;
	}

	public record WeeklyGoalProgress(
			Instant weekStartsAt,
			Instant weekEndsAt,
			long completedDurationSeconds,
			long remainingDurationSeconds,
			double durationProgress,
			boolean durationAchieved,
			long completedTrainingCount,
			long remainingTrainingCount,
			double countProgress,
			boolean countAchieved,
			List<TrainingTypeDuration> trainingTypeDurations) {
	}

	public record TrainingTypeDuration(
			SceneType type,
			long durationSeconds,
			double percentage) {
	}
}
