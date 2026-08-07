package com.unispeaking.service.achievement.impl;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.achievement.AchievementAcknowledgeRequest;
import com.unispeaking.domain.dto.achievement.AchievementAcknowledgeResponse;
import com.unispeaking.domain.dto.achievement.AchievementMilestoneResponse;
import com.unispeaking.domain.dto.achievement.AchievementNotificationResponse;
import com.unispeaking.domain.dto.achievement.AchievementOverviewResponse;
import com.unispeaking.domain.dto.achievement.AchievementSeriesResponse;
import com.unispeaking.domain.dto.achievement.AchievementSyncResponse;
import com.unispeaking.domain.po.achievement.AchievementUnlockCreation;
import com.unispeaking.domain.po.achievement.UserAchievementState;
import com.unispeaking.domain.po.achievement.UserAchievementUnlock;
import com.unispeaking.domain.vo.achievement.AchievementDefinition;
import com.unispeaking.domain.vo.achievement.AchievementMetricSnapshot;
import com.unispeaking.domain.vo.achievement.AchievementSeriesDefinition;
import com.unispeaking.domain.vo.achievement.AchievementSeriesProgress;
import com.unispeaking.infrastructure.persistence.repository.achievement.AchievementUnlockRepository;
import com.unispeaking.component.achievement.AchievementCatalog;
import com.unispeaking.component.achievement.AchievementMetricCalculator;
import com.unispeaking.component.achievement.AchievementProgressCalculator;
import com.unispeaking.service.achievement.AchievementService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AchievementServiceImpl implements AchievementService {

	private final AchievementMetricCalculator metricCalculator;
	private final AchievementProgressCalculator progressCalculator;
	private final AchievementUnlockRepository unlocks;
	private final Map<String, MilestoneContext> milestoneContexts;
	private final Map<String, Integer> catalogOrder;

	public AchievementServiceImpl(
			AchievementCatalog catalog,
			AchievementMetricCalculator metricCalculator,
			AchievementProgressCalculator progressCalculator,
			AchievementUnlockRepository unlocks) {
		this.metricCalculator = metricCalculator;
		this.progressCalculator = progressCalculator;
		this.unlocks = unlocks;
		this.milestoneContexts = buildMilestoneContexts(catalog.series());
		this.catalogOrder = buildCatalogOrder(catalog.series());
	}

	@Override
	@Transactional(readOnly = true)
	public AchievementOverviewResponse getOverview(UUID userId) {
		AchievementMetricSnapshot metrics = metricCalculator.calculate(userId);
		return buildOverview(metrics, unlocks.findAll(userId));
	}

	@Override
	public AchievementSyncResponse synchronize(UUID userId) {
		AchievementMetricSnapshot metrics = metricCalculator.calculate(userId);
		List<AchievementDefinition> reached = progressCalculator.calculate(metrics)
				.stream()
				.flatMap(progress -> progress.reachedMilestones().stream())
				.toList();
		Instant synchronizedAt = Instant.now();
		if (unlocks.findState(userId).isEmpty()) {
			initializeSilently(userId, reached, synchronizedAt);
			List<UserAchievementUnlock> allUnlocks = unlocks.findAll(userId);
			return new AchievementSyncResponse(
					true,
					buildOverview(metrics, allUnlocks),
					List.of(),
					List.of());
		}

		Set<String> existingIds = unlocks.findAll(userId).stream()
				.map(UserAchievementUnlock::achievementId)
				.collect(Collectors.toSet());
		List<UserAchievementUnlock> newlyCreated = new ArrayList<>();
		for (AchievementDefinition achievement : reached) {
			if (existingIds.contains(achievement.achievementId())) {
				continue;
			}
			AchievementUnlockCreation creation = unlocks.create(
					new UserAchievementUnlock(
							userId,
							achievement.achievementId(),
							synchronizedAt,
							null));
			if (creation.created()) {
				newlyCreated.add(creation.unlock());
			}
		}
		List<UserAchievementUnlock> allUnlocks = unlocks.findAll(userId);
		return new AchievementSyncResponse(
				true,
				buildOverview(metrics, allUnlocks),
				toNotifications(newlyCreated),
				toNotifications(unlocks.findPending(userId)));
	}

	@Override
	@Transactional
	public AchievementAcknowledgeResponse acknowledge(
			UUID userId,
			String achievementId,
			AchievementAcknowledgeRequest request) {
		if (request == null || !Boolean.TRUE.equals(request.acknowledged())) {
			throw new BusinessException(
					"ACHIEVEMENT_ACKNOWLEDGEMENT_INVALID",
					"acknowledged 必须为 true");
		}
		String normalizedId = achievementId == null ? "" : achievementId.trim();
		if (!milestoneContexts.containsKey(normalizedId)) {
			throw unlockNotFound();
		}
		UserAchievementUnlock acknowledged = unlocks.acknowledge(
				userId,
				normalizedId,
				Instant.now());
		return new AchievementAcknowledgeResponse(
				acknowledged.achievementId(),
				acknowledged.acknowledgedAt());
	}

	private void initializeSilently(
			UUID userId,
			List<AchievementDefinition> reached,
			Instant initializedAt) {
		for (AchievementDefinition achievement : reached) {
			unlocks.create(new UserAchievementUnlock(
					userId,
					achievement.achievementId(),
					initializedAt,
					initializedAt));
		}
		unlocks.initialize(new UserAchievementState(userId, initializedAt));
	}

	private AchievementOverviewResponse buildOverview(
			AchievementMetricSnapshot metrics,
			List<UserAchievementUnlock> userUnlocks) {
		Map<String, UserAchievementUnlock> unlocksById = userUnlocks.stream()
				.filter(unlock -> milestoneContexts.containsKey(
						unlock.achievementId()))
				.collect(Collectors.toMap(
						UserAchievementUnlock::achievementId,
						Function.identity(),
						(first, ignored) -> first));
		List<AchievementSeriesProgress> progress =
				progressCalculator.calculate(metrics);
		return new AchievementOverviewResponse(progress.stream()
				.map(item -> toSeriesResponse(item, unlocksById))
				.toList());
	}

	private AchievementSeriesResponse toSeriesResponse(
			AchievementSeriesProgress progress,
			Map<String, UserAchievementUnlock> unlocksById) {
		AchievementSeriesDefinition series = progress.series();
		List<AchievementDefinition> persistedMilestones = series.milestones()
				.stream()
				.filter(milestone -> unlocksById.containsKey(
						milestone.achievementId()))
				.toList();
		AchievementDefinition current = persistedMilestones.isEmpty()
				? null
				: persistedMilestones.getLast();
		AchievementDefinition next = series.milestones().stream()
				.filter(milestone -> current == null
						|| milestone.level() > current.level())
				.findFirst()
				.orElse(null);
		return new AchievementSeriesResponse(
				series.seriesId(),
				series.category(),
				series.title(),
				series.unit(),
				progress.currentValue(),
				current == null ? 0 : current.level(),
				current == null ? null : current.title(),
				next == null ? null : next.level(),
				next == null ? null : next.title(),
				next == null ? null : next.threshold(),
				next == null,
				series.milestones().stream()
						.map(milestone -> toMilestoneResponse(
								milestone,
								unlocksById.get(milestone.achievementId())))
						.toList());
	}

	private AchievementMilestoneResponse toMilestoneResponse(
			AchievementDefinition milestone,
			UserAchievementUnlock unlock) {
		return new AchievementMilestoneResponse(
				milestone.achievementId(),
				milestone.level(),
				milestone.title(),
				milestone.description(),
				milestone.threshold(),
				unlock != null,
				unlock == null ? null : unlock.unlockedAt());
	}

	private List<AchievementNotificationResponse> toNotifications(
			List<UserAchievementUnlock> userUnlocks) {
		return userUnlocks.stream()
				.filter(unlock -> milestoneContexts.containsKey(
						unlock.achievementId()))
				.sorted(Comparator
						.comparing(UserAchievementUnlock::unlockedAt)
						.thenComparingInt(unlock -> catalogOrder.get(
								unlock.achievementId())))
				.map(this::toNotification)
				.toList();
	}

	private AchievementNotificationResponse toNotification(
			UserAchievementUnlock unlock) {
		MilestoneContext context = milestoneContexts.get(unlock.achievementId());
		return new AchievementNotificationResponse(
				unlock.achievementId(),
				context.series().seriesId(),
				context.series().category(),
				context.series().title(),
				context.milestone().level(),
				context.milestone().title(),
				context.milestone().description(),
				unlock.unlockedAt(),
				unlock.acknowledgedAt());
	}

	private Map<String, MilestoneContext> buildMilestoneContexts(
			List<AchievementSeriesDefinition> series) {
		Map<String, MilestoneContext> contexts = new HashMap<>();
		for (AchievementSeriesDefinition item : series) {
			for (AchievementDefinition milestone : item.milestones()) {
				contexts.put(
						milestone.achievementId(),
						new MilestoneContext(item, milestone));
			}
		}
		return Map.copyOf(contexts);
	}

	private Map<String, Integer> buildCatalogOrder(
			List<AchievementSeriesDefinition> series) {
		Map<String, Integer> order = new HashMap<>();
		int index = 0;
		for (AchievementSeriesDefinition item : series) {
			for (AchievementDefinition milestone : item.milestones()) {
				order.put(milestone.achievementId(), index++);
			}
		}
		return Map.copyOf(order);
	}

	private BusinessException unlockNotFound() {
		return new BusinessException(
				"ACHIEVEMENT_UNLOCK_NOT_FOUND",
				"成就尚未解锁");
	}

	private record MilestoneContext(
			AchievementSeriesDefinition series,
			AchievementDefinition milestone) {
	}
}
