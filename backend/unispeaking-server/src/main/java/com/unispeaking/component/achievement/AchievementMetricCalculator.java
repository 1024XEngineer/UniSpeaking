package com.unispeaking.component.achievement;

import com.unispeaking.domain.po.achievement.AchievementEvaluationFact;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.achievement.AchievementMetricSnapshot;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.SceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AchievementMetricCalculator {

	static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");
	static final long MINIMUM_VALID_SECONDS = 30;
	private static final BigDecimal QUALITY_SCORE_THRESHOLD =
			BigDecimal.valueOf(80);
	private static final BigDecimal MAXIMUM_SCORE = BigDecimal.valueOf(100);

	private final PracticeSessionRepository practiceSessions;
	private final SceneRepository scenes;
	private final SessionEvaluationRepository sessionEvaluations;
	private final TurnEvaluationRepository turnEvaluations;
	private final SceneSentenceReadingRepository sentenceReadings;

	public AchievementMetricCalculator(
			PracticeSessionRepository practiceSessions,
			SceneRepository scenes,
			SessionEvaluationRepository sessionEvaluations,
			TurnEvaluationRepository turnEvaluations,
			SceneSentenceReadingRepository sentenceReadings) {
		this.practiceSessions = practiceSessions;
		this.scenes = scenes;
		this.sessionEvaluations = sessionEvaluations;
		this.turnEvaluations = turnEvaluations;
		this.sentenceReadings = sentenceReadings;
	}

	public AchievementMetricSnapshot calculate(UUID userId) {
		List<PracticeSessionRecord> completedSessions =
				practiceSessions.findCompletedByUserId(userId);
		List<PracticeSessionRecord> validSessions = completedSessions.stream()
				.filter(this::isValidPractice)
				.toList();
		List<String> sessionIds = completedSessions.stream()
				.map(PracticeSessionRecord::sessionId)
				.distinct()
				.toList();
		List<String> sceneIds = scenes.findAllIdsByUserId(userId.toString());
		List<AchievementEvaluationFact> evaluationFacts =
				sessionEvaluations.findAchievementFacts(sessionIds, sceneIds);
		List<LocalDate> checkinDates = evaluationFacts.stream()
				.map(AchievementEvaluationFact::createdAt)
				.filter(createdAt -> createdAt != null)
				.map(createdAt -> createdAt.atZoneSameInstant(BUSINESS_ZONE_ID)
						.toLocalDate())
				.distinct()
				.sorted()
				.toList();
		BigDecimal bestExpressionScore = turnEvaluations.findBestOverallScore(
					sessionIds,
					sceneIds)
				.orElse(BigDecimal.ZERO)
				.min(MAXIMUM_SCORE);

		return new AchievementMetricSnapshot(
				validSessions.size(),
				longestStreak(checkinDates),
				validSessions.stream()
						.map(PracticeSessionRecord::sceneId)
						.filter(sceneId -> sceneId != null && !sceneId.isBlank())
						.distinct()
						.count(),
				bestExpressionScore,
				sentenceReadings.countAttemptsBySceneIds(sceneIds),
				scenes.countAllByUserId(userId.toString()),
				bestMonthlyCheckinDays(checkinDates),
				validSessions.stream()
						.mapToLong(this::practiceSeconds)
						.sum(),
				checkinDates.size(),
				evaluationFacts.stream()
						.filter(fact -> fact.finalScore() != null)
						.filter(fact -> fact.finalScore().compareTo(
								QUALITY_SCORE_THRESHOLD) >= 0)
						.count());
	}

	private boolean isValidPractice(PracticeSessionRecord session) {
		return session.startedAt() != null
				&& session.endedAt() != null
				&& practiceSeconds(session) >= MINIMUM_VALID_SECONDS;
	}

	private long practiceSeconds(PracticeSessionRecord session) {
		return Math.max(
				0,
				Duration.between(session.startedAt(), session.endedAt()).toSeconds());
	}

	private int longestStreak(List<LocalDate> sortedDates) {
		int longest = 0;
		int current = 0;
		LocalDate previous = null;
		for (LocalDate date : sortedDates) {
			current = previous != null && date.equals(previous.plusDays(1))
					? current + 1
					: 1;
			longest = Math.max(longest, current);
			previous = date;
		}
		return longest;
	}

	private int bestMonthlyCheckinDays(List<LocalDate> dates) {
		Map<YearMonth, Long> daysByMonth = dates.stream()
				.collect(Collectors.groupingBy(
						YearMonth::from,
						Collectors.counting()));
		return daysByMonth.values().stream()
				.max(Comparator.naturalOrder())
				.map(Math::toIntExact)
				.orElse(0);
	}
}
