package com.unispeaking.infrastructure.persistence.repository.user;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.profile.WeeklyLearningGoals;
import com.unispeaking.infrastructure.persistence.entity.user.UserPreferenceEntity;
import com.unispeaking.infrastructure.persistence.mapper.user.UserPreferenceMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class WeeklyLearningGoalRepository {

	private final UserPreferenceMapper mapper;

	public WeeklyLearningGoalRepository(UserPreferenceMapper mapper) {
		this.mapper = mapper;
	}

	public Optional<WeeklyLearningGoals> findByUserId(UUID userId) {
		try {
			return Optional.ofNullable(mapper.selectById(userId))
					.map(entity -> WeeklyLearningGoals.fromStored(
							entity.getWeeklyDurationTargetMinutes(),
							entity.getWeeklyTrainingCountTarget()));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public WeeklyLearningGoals save(UUID userId, WeeklyLearningGoals goals) {
		try {
			UserPreferenceEntity entity = mapper.selectById(userId);
			OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
			if (entity == null) {
				entity = new UserPreferenceEntity();
				entity.setUserId(userId);
				entity.setCreatedAt(now);
				entity.setUpdatedAt(now);
				copyGoals(goals, entity);
				if (mapper.insert(entity) != 1) {
					throw persistenceFailure();
				}
				return goals;
			}
			copyGoals(goals, entity);
			entity.setUpdatedAt(now);
			if (mapper.updateById(entity) != 1) {
				throw persistenceFailure();
			}
			return goals;
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private void copyGoals(
			WeeklyLearningGoals goals,
			UserPreferenceEntity entity) {
		entity.setWeeklyDurationTargetMinutes(goals.durationTargetMinutes());
		entity.setWeeklyTrainingCountTarget(goals.trainingCountTarget());
	}

	private BusinessException persistenceFailure() {
		return new BusinessException(
				"PROFILE_GOALS_PERSISTENCE_FAILED",
				"学习目标保存失败");
	}
}
