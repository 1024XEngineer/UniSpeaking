package com.unispeaking.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unispeaking.domain.po.achievement.AchievementDefinition;
import com.unispeaking.domain.po.achievement.UserAchievementProgress;
import com.unispeaking.domain.vo.achievement.AchievementMetricKey;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.infrastructure.persistence.mybatis.entity.AchievementDefinitionEntity;
import com.unispeaking.infrastructure.persistence.mybatis.entity.UserAchievementProgressEntity;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.AchievementDefinitionMapper;
import com.unispeaking.infrastructure.persistence.mybatis.mapper.UserAchievementProgressMapper;
import com.unispeaking.repository.AchievementRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisAchievementRepository implements AchievementRepository {

	private final AchievementDefinitionMapper definitionMapper;
	private final UserAchievementProgressMapper progressMapper;

	public MybatisAchievementRepository(
			AchievementDefinitionMapper definitionMapper,
			UserAchievementProgressMapper progressMapper) {
		this.definitionMapper = definitionMapper;
		this.progressMapper = progressMapper;
	}

	@Override
	public List<AchievementDefinition> findActiveDefinitions() {
		return definitionMapper.selectList(Wrappers
						.<AchievementDefinitionEntity>lambdaQuery()
						.eq(AchievementDefinitionEntity::getStatus, "ACTIVE")
						.orderByAsc(AchievementDefinitionEntity::getSortOrder)
						.orderByAsc(AchievementDefinitionEntity::getCode))
				.stream()
				.map(this::toDefinition)
				.toList();
	}

	@Override
	public Optional<UserAchievementProgress> findProgress(
			UUID userId,
			UUID achievementId) {
		return Optional.ofNullable(progressMapper.find(userId, achievementId))
				.map(this::toProgress);
	}

	@Override
	public UserAchievementProgress upsertProgress(UserAchievementProgress progress) {
		int affected = progressMapper.upsert(toEntity(progress));
		if (affected != 1) {
			throw new IllegalStateException("Achievement progress upsert affected " + affected);
		}
		return findProgress(progress.userId(), progress.achievementId())
				.orElseThrow(() -> new IllegalStateException(
						"Achievement progress missing after upsert"));
	}

	private AchievementDefinition toDefinition(AchievementDefinitionEntity entity) {
		AchievementMetricKey metricKey;
		try {
			metricKey = AchievementMetricKey.valueOf(entity.getMetricKey());
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException(
					"ACHIEVEMENT_METRIC_UNSUPPORTED",
					"成就指标尚未支持：" + entity.getMetricKey());
		}
		return new AchievementDefinition(
				entity.getId(),
				entity.getCode(),
				entity.getName(),
				entity.getDescription(),
				entity.getCategory(),
				metricKey,
				entity.getTargetValue(),
				entity.getIconKey(),
				entity.getSortOrder(),
				toInstant(entity.getCreatedAt()),
				toInstant(entity.getUpdatedAt()));
	}

	private UserAchievementProgressEntity toEntity(UserAchievementProgress progress) {
		UserAchievementProgressEntity entity = new UserAchievementProgressEntity();
		entity.setUserId(progress.userId());
		entity.setAchievementId(progress.achievementId());
		entity.setProgressValue(progress.progressValue());
		entity.setUnlockedAt(toOffsetDateTime(progress.unlockedAt()));
		entity.setCreatedAt(toOffsetDateTime(progress.createdAt()));
		entity.setUpdatedAt(toOffsetDateTime(progress.updatedAt()));
		return entity;
	}

	private UserAchievementProgress toProgress(UserAchievementProgressEntity entity) {
		return new UserAchievementProgress(
				entity.getUserId(),
				entity.getAchievementId(),
				entity.getProgressValue(),
				toInstant(entity.getUnlockedAt()),
				toInstant(entity.getCreatedAt()),
				toInstant(entity.getUpdatedAt()));
	}

	private OffsetDateTime toOffsetDateTime(Instant value) {
		return value == null ? null : value.atOffset(ZoneOffset.UTC);
	}

	private Instant toInstant(OffsetDateTime value) {
		return value == null ? null : value.toInstant();
	}
}
