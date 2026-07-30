package com.unispeaking.service.achievement.impl;

import com.unispeaking.domain.dto.achievement.AchievementCollectionResponse;
import com.unispeaking.domain.dto.achievement.AchievementItemResponse;
import com.unispeaking.domain.po.achievement.AchievementDefinition;
import com.unispeaking.domain.po.achievement.UserAchievementProgress;
import com.unispeaking.domain.vo.achievement.AchievementMetricKey;
import com.unispeaking.repository.AchievementRepository;
import com.unispeaking.service.achievement.AchievementService;
import com.unispeaking.service.profile.query.AchievementMetricQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean(AchievementMetricQueryPort.class)
public class AchievementServiceImpl implements AchievementService {

	private final AchievementRepository repository;
	private final AchievementMetricQueryPort metricQueryPort;
	private final Clock clock;

	@Autowired
	public AchievementServiceImpl(
			AchievementRepository repository,
			AchievementMetricQueryPort metricQueryPort) {
		this(repository, metricQueryPort, Clock.systemUTC());
	}

	public AchievementServiceImpl(
			AchievementRepository repository,
			AchievementMetricQueryPort metricQueryPort,
			Clock clock) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.metricQueryPort = Objects.requireNonNull(metricQueryPort, "metricQueryPort");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	@Transactional
	public AchievementCollectionResponse synchronize(UUID userId) {
		Objects.requireNonNull(userId, "userId");
		List<AchievementDefinition> definitions = repository.findActiveDefinitions();
		if (definitions.isEmpty()) {
			return AchievementCollectionResponse.empty();
		}

		Map<AchievementMetricKey, Long> metricValues =
				new EnumMap<>(AchievementMetricKey.class);
		List<AchievementItemResponse> items = new ArrayList<>(definitions.size());
		for (AchievementDefinition definition : definitions) {
			long rawValue = metricValues.computeIfAbsent(
					definition.metricKey(),
					metricKey -> metricQueryPort.metricValue(userId, metricKey));
			items.add(synchronizeOne(userId, definition, rawValue));
		}
		long unlockedCount = items.stream()
				.filter(AchievementItemResponse::unlocked)
				.count();
		return new AchievementCollectionResponse(
				unlockedCount,
				items.size(),
				items);
	}

	private AchievementItemResponse synchronizeOne(
			UUID userId,
			AchievementDefinition definition,
			long rawValue) {
		Instant now = clock.instant();
		UserAchievementProgress existing = repository
				.findProgress(userId, definition.id())
				.orElse(null);
		long progressValue = Math.min(
				Math.max(0, rawValue),
				definition.targetValue());
		Instant unlockedAt = existing == null ? null : existing.unlockedAt();
		if (unlockedAt == null && progressValue >= definition.targetValue()) {
			unlockedAt = now;
		}
		UserAchievementProgress progress = new UserAchievementProgress(
				userId,
				definition.id(),
				progressValue,
				unlockedAt,
				existing == null ? now : existing.createdAt(),
				now);
		return AchievementItemResponse.from(
				definition,
				repository.upsertProgress(progress));
	}
}
