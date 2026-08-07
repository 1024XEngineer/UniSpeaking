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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Repository
public class WeeklyLearningGoalRepository {
	private static final String DURATION_TARGET_KEY =
			"weekly_duration_target_minutes";
	private static final String TRAINING_COUNT_TARGET_KEY =
			"weekly_training_count_target";

	private final UserPreferenceMapper mapper;
	private final ObjectMapper objectMapper;

	public WeeklyLearningGoalRepository(
			UserPreferenceMapper mapper,
			ObjectMapper objectMapper) {
		this.mapper = mapper;
		this.objectMapper = objectMapper;
	}

	public Optional<WeeklyLearningGoals> findByUserId(UUID userId) {
		try {
			return Optional.ofNullable(mapper.selectById(userId))
					.map(this::toDomain);
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
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
			throw persistenceFailure(exception);
		}
	}

	private void copyGoals(
			WeeklyLearningGoals goals,
			UserPreferenceEntity entity) {
		ObjectNode preferences = preferencesObject(entity.getPreferences());
		preferences.put(DURATION_TARGET_KEY, goals.durationTargetMinutes());
		preferences.put(TRAINING_COUNT_TARGET_KEY, goals.trainingCountTarget());
		entity.setPreferences(writePreferences(preferences));
		// Retain the transient values for callers and unit tests. MyBatis ignores
		// these fields because the deployed user_preference table has no columns
		// with these names.
		entity.setWeeklyDurationTargetMinutes(goals.durationTargetMinutes());
		entity.setWeeklyTrainingCountTarget(goals.trainingCountTarget());
	}

	private WeeklyLearningGoals toDomain(UserPreferenceEntity entity) {
		JsonNode preferences = preferencesObject(entity.getPreferences());
		return WeeklyLearningGoals.fromStored(
				integerValue(
						preferences,
						DURATION_TARGET_KEY,
						entity.getWeeklyDurationTargetMinutes()),
				integerValue(
						preferences,
						TRAINING_COUNT_TARGET_KEY,
						entity.getWeeklyTrainingCountTarget()));
	}

	private ObjectNode preferencesObject(String json) {
		if (json == null || json.isBlank()) {
			return objectMapper.createObjectNode();
		}
		try {
			JsonNode parsed = objectMapper.readTree(json);
			if (parsed instanceof ObjectNode objectNode) {
				return objectNode;
			}
			throw new IllegalStateException("User preferences must be a JSON object");
		}
		catch (JacksonException exception) {
			throw new IllegalStateException("Invalid user preferences JSON", exception);
		}
	}

	private Integer integerValue(
			JsonNode preferences,
			String key,
			Integer fallback) {
		JsonNode value = preferences.get(key);
		if (value != null && value.isIntegralNumber()) {
			return Integer.valueOf(value.intValue());
		}
		return fallback;
	}

	private String writePreferences(ObjectNode preferences) {
		try {
			return objectMapper.writeValueAsString(preferences);
		}
		catch (JacksonException exception) {
			throw new IllegalStateException("Unable to serialize user preferences", exception);
		}
	}

	private BusinessException persistenceFailure() {
		return new BusinessException(
				"PROFILE_GOALS_PERSISTENCE_FAILED",
				"学习目标保存失败");
	}

	private BusinessException persistenceFailure(RuntimeException cause) {
		BusinessException failure = persistenceFailure();
		failure.initCause(cause);
		return failure;
	}
}
