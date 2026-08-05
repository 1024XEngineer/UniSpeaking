package com.unispeaking.service.profile.impl;

import com.unispeaking.domain.dto.profile.ProfileInsightsResponse;
import com.unispeaking.domain.dto.profile.UpdateWeeklyLearningGoalsRequest;
import com.unispeaking.domain.po.profile.WeeklyLearningGoals;
import com.unispeaking.infrastructure.config.ProfileProperties;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.user.WeeklyLearningGoalRepository;
import com.unispeaking.service.profile.ProfileInsightsService;
import com.unispeaking.service.profile.WeeklyGoalProgressCalculator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProfileInsightsServiceImpl implements ProfileInsightsService {

	private final WeeklyLearningGoalRepository goals;
	private final PracticeSessionRepository practiceSessions;
	private final ZoneId zoneId;
	private final Clock clock;
	private final WeeklyGoalProgressCalculator calculator;

	@Autowired
	public ProfileInsightsServiceImpl(
			WeeklyLearningGoalRepository goals,
			PracticeSessionRepository practiceSessions,
			ProfileProperties profileProperties) {
		this(
				goals,
				practiceSessions,
				profileProperties.zoneId(),
				Clock.system(profileProperties.zoneId()));
	}

	ProfileInsightsServiceImpl(
			WeeklyLearningGoalRepository goals,
			PracticeSessionRepository practiceSessions,
			ZoneId zoneId,
			Clock clock) {
		this.goals = goals;
		this.practiceSessions = practiceSessions;
		this.zoneId = zoneId;
		this.clock = clock;
		this.calculator = new WeeklyGoalProgressCalculator();
	}

	@Override
	public ProfileInsightsResponse getInsights(String userId) {
		UUID id = UUID.fromString(userId);
		WeeklyLearningGoals targets = goals.findByUserId(id)
				.orElseGet(WeeklyLearningGoals::defaults);
		Instant now = clock.instant();
		Instant weekStart = now.atZone(zoneId)
				.toLocalDate()
				.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
				.atStartOfDay(zoneId)
				.toInstant();
		var progress = calculator.calculate(
				practiceSessions.findCompletedOverlapping(id, weekStart, now),
				targets,
				now,
				zoneId);
		return new ProfileInsightsResponse(new ProfileInsightsResponse.WeeklyGoals(
				progress.weekStartsAt().atZone(zoneId).toOffsetDateTime(),
				progress.weekEndsAt().atZone(zoneId).toOffsetDateTime(),
				targets.durationTargetMinutes(),
				progress.completedDurationSeconds(),
				progress.remainingDurationSeconds(),
				progress.durationProgress(),
				progress.durationAchieved(),
				targets.trainingCountTarget(),
				progress.completedTrainingCount(),
				progress.remainingTrainingCount(),
				progress.countProgress(),
				progress.countAchieved()));
	}

	@Override
	public ProfileInsightsResponse updateGoals(
			String userId,
			UpdateWeeklyLearningGoalsRequest request) {
		goals.save(UUID.fromString(userId), request.toDomain());
		return getInsights(userId);
	}
}
