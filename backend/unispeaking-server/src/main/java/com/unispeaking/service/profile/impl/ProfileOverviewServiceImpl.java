package com.unispeaking.service.profile.impl;

import com.unispeaking.domain.dto.profile.LearningCalendarDayResponse;
import com.unispeaking.domain.dto.profile.LearningCalendarResponse;
import com.unispeaking.domain.dto.profile.LearningSummaryResponse;
import com.unispeaking.domain.dto.profile.ProfileOverviewResponse;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.service.achievement.AchievementService;
import com.unispeaking.service.profile.ProfileOverviewService;
import com.unispeaking.service.profile.query.LearningAssetCountPort;
import com.unispeaking.service.profile.query.LearningStatisticsQueryPort;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean({
	LearningStatisticsQueryPort.class,
	LearningAssetCountPort.class,
	AchievementService.class
})
public class ProfileOverviewServiceImpl implements ProfileOverviewService {

	private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

	private final LearningStatisticsQueryPort statisticsPort;
	private final LearningAssetCountPort assetCountPort;
	private final AchievementService achievementService;
	private final Clock clock;

	@Autowired
	public ProfileOverviewServiceImpl(
			LearningStatisticsQueryPort statisticsPort,
			LearningAssetCountPort assetCountPort,
			AchievementService achievementService) {
		this(statisticsPort, assetCountPort, achievementService, Clock.systemUTC());
	}

	public ProfileOverviewServiceImpl(
			LearningStatisticsQueryPort statisticsPort,
			LearningAssetCountPort assetCountPort,
			AchievementService achievementService,
			Clock clock) {
		this.statisticsPort = Objects.requireNonNull(statisticsPort, "statisticsPort");
		this.assetCountPort = Objects.requireNonNull(assetCountPort, "assetCountPort");
		this.achievementService = Objects.requireNonNull(
				achievementService,
				"achievementService");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public ProfileOverviewResponse getOverview(UUID userId, YearMonth yearMonth) {
		Objects.requireNonNull(userId, "userId");
		YearMonth resolvedMonth = yearMonth == null
				? YearMonth.now(clock.withZone(BUSINESS_ZONE))
				: yearMonth;
		try {
			LearningStatisticsQueryPort.LearningSummary learningSummary =
					statisticsPort.summary(userId, BUSINESS_ZONE);
			long savedAssetCount = assetCountPort.countSavedAssets(userId);
			List<LearningStatisticsQueryPort.LearningCalendarDay> learningDays =
					statisticsPort.calendar(userId, resolvedMonth, BUSINESS_ZONE);
			Map<LocalDate, Long> assetsByDate = assetCountPort
					.countSavedAssetsByDate(userId, resolvedMonth, BUSINESS_ZONE);
			return new ProfileOverviewResponse(
					new LearningSummaryResponse(
							learningSummary.weeklyMinutes(),
							savedAssetCount,
							learningSummary.continuousLearningDays()),
					new LearningCalendarResponse(
							resolvedMonth,
							mergeCalendar(resolvedMonth, learningDays, assetsByDate)),
					achievementService.synchronize(userId));
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw new BusinessException(
					"PROFILE_OVERVIEW_UNAVAILABLE",
					"个人概览暂时不可用，请稍后重试");
		}
	}

	private List<LearningCalendarDayResponse> mergeCalendar(
			YearMonth yearMonth,
			List<LearningStatisticsQueryPort.LearningCalendarDay> learningDays,
			Map<LocalDate, Long> assetsByDate) {
		TreeMap<LocalDate, CalendarAccumulator> merged = new TreeMap<>();
		for (LearningStatisticsQueryPort.LearningCalendarDay day : learningDays) {
			if (isInMonth(day.date(), yearMonth)) {
				merged.computeIfAbsent(day.date(), ignored -> new CalendarAccumulator())
						.addLearning(day.learningMinutes(), day.practiceCount());
			}
		}
		for (Map.Entry<LocalDate, Long> entry : assetsByDate.entrySet()) {
			if (isInMonth(entry.getKey(), yearMonth)) {
				merged.computeIfAbsent(
								entry.getKey(),
								ignored -> new CalendarAccumulator())
						.addAssets(entry.getValue());
			}
		}
		List<LearningCalendarDayResponse> days = new ArrayList<>(merged.size());
		merged.forEach((date, value) -> days.add(new LearningCalendarDayResponse(
				date,
				value.learningMinutes,
				value.practiceCount,
				value.savedAssetCount)));
		return List.copyOf(days);
	}

	private boolean isInMonth(LocalDate date, YearMonth yearMonth) {
		return date != null && YearMonth.from(date).equals(yearMonth);
	}

	private static final class CalendarAccumulator {

		private long learningMinutes;
		private int practiceCount;
		private long savedAssetCount;

		void addLearning(long minutes, int count) {
			learningMinutes += Math.max(0, minutes);
			practiceCount += Math.max(0, count);
		}

		void addAssets(long count) {
			savedAssetCount += Math.max(0, count);
		}
	}
}
