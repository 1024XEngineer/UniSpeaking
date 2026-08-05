package com.unispeaking.service.profile.impl;

import com.unispeaking.domain.dto.profile.ProfileInsightsResponse;
import com.unispeaking.domain.dto.profile.UpdateWeeklyLearningGoalsRequest;
import com.unispeaking.domain.po.evaluation.SessionScoreSnapshot;
import com.unispeaking.domain.po.profile.WeeklyLearningGoals;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.infrastructure.config.ProfileProperties;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.persistence.repository.user.WeeklyLearningGoalRepository;
import com.unispeaking.service.profile.AbilityWeaknessCalculator;
import com.unispeaking.service.profile.ProfileInsightsService;
import com.unispeaking.service.profile.WeeklyGoalProgressCalculator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProfileInsightsServiceImpl implements ProfileInsightsService {

	private final WeeklyLearningGoalRepository goals;
	private final PracticeSessionRepository practiceSessions;
	private final SessionEvaluationRepository sessionEvaluations;
	private final ZoneId zoneId;
	private final Clock clock;
	private final WeeklyGoalProgressCalculator calculator;
	private final AbilityWeaknessCalculator weaknessCalculator;

	@Autowired
	public ProfileInsightsServiceImpl(
			WeeklyLearningGoalRepository goals,
			PracticeSessionRepository practiceSessions,
			SessionEvaluationRepository sessionEvaluations,
			ProfileProperties profileProperties) {
		this(
				goals,
				practiceSessions,
				sessionEvaluations,
				profileProperties.zoneId(),
				Clock.system(profileProperties.zoneId()));
	}

	ProfileInsightsServiceImpl(
			WeeklyLearningGoalRepository goals,
			PracticeSessionRepository practiceSessions,
			SessionEvaluationRepository sessionEvaluations,
			ZoneId zoneId,
			Clock clock) {
		this.goals = goals;
		this.practiceSessions = practiceSessions;
		this.sessionEvaluations = sessionEvaluations;
		this.zoneId = zoneId;
		this.clock = clock;
		this.calculator = new WeeklyGoalProgressCalculator();
		this.weaknessCalculator = new AbilityWeaknessCalculator();
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
		var weeklyGoals = new ProfileInsightsResponse.WeeklyGoals(
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
				progress.countAchieved());
		List<ProfileInsightsResponse.TrainingTypeDistribution> distribution =
				progress.trainingTypeDurations().stream()
						.map(item -> new ProfileInsightsResponse.TrainingTypeDistribution(
								item.type().name(),
								item.durationSeconds(),
								item.percentage()))
						.toList();
		List<ProfileInsightsResponse.AbilityTrendPoint> abilityTrends =
				abilityTrends(id);
		var weaknessResult = weaknessCalculator.calculate(abilityTrends);
		return new ProfileInsightsResponse(
				weeklyGoals,
				distribution,
				abilityTrends,
				weaknessResult.analysis(),
				weaknessResult.weaknesses(),
				weaknessResult.recommendations());
	}

	private List<ProfileInsightsResponse.AbilityTrendPoint> abilityTrends(
			UUID userId) {
		List<PracticeSessionRecord> completed =
				practiceSessions.findCompletedByUserId(userId);
		if (completed.isEmpty()) {
			return List.of();
		}
		Map<String, SessionScoreSnapshot> scoresBySession =
				sessionEvaluations.findScoreSnapshotsBySessionIds(
						completed.stream()
								.map(PracticeSessionRecord::sessionId)
								.toList())
						.stream()
						.collect(Collectors.toMap(
								SessionScoreSnapshot::sessionId,
								Function.identity()));
		Comparator<PracticeSessionRecord> byCompletedAt =
				Comparator.comparing(PracticeSessionRecord::endedAt);
		return completed.stream()
				.filter(record -> scoresBySession.containsKey(record.sessionId()))
				.sorted(byCompletedAt.reversed())
				.limit(10)
				.sorted(byCompletedAt)
				.map(record -> toAbilityTrendPoint(
						record,
						scoresBySession.get(record.sessionId())))
				.toList();
	}

	private ProfileInsightsResponse.AbilityTrendPoint toAbilityTrendPoint(
			PracticeSessionRecord session,
			SessionScoreSnapshot scores) {
		return new ProfileInsightsResponse.AbilityTrendPoint(
				session.sessionId(),
				session.endedAt().atZone(zoneId).toOffsetDateTime(),
				session.sceneType().name(),
				new ProfileInsightsResponse.AbilityScores(
						scores.accuracy(),
						scores.fluency(),
						scores.grammar(),
						scores.vocabulary(),
						scores.naturalness()));
	}

	@Override
	public ProfileInsightsResponse updateGoals(
			String userId,
			UpdateWeeklyLearningGoalsRequest request) {
		goals.save(UUID.fromString(userId), request.toDomain());
		return getInsights(userId);
	}
}
