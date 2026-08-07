package com.unispeaking.infrastructure.persistence.repository.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.profile.WeeklyLearningGoals;
import com.unispeaking.infrastructure.persistence.entity.user.UserPreferenceEntity;
import com.unispeaking.infrastructure.persistence.mapper.user.UserPreferenceMapper;
import java.util.UUID;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class WeeklyLearningGoalRepositoryTest {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeAll
	static void initializeMybatisMetadata() {
		TableInfoHelper.initTableInfo(
				new MapperBuilderAssistant(
						new MybatisConfiguration(),
						"weekly-learning-goal-repository-test"),
				UserPreferenceEntity.class);
	}

	@Test
	void excludesJsonBackedGoalsFromGeneratedPreferenceSql() {
		var mappedColumns = TableInfoHelper.getTableInfo(UserPreferenceEntity.class)
				.getFieldList()
				.stream()
				.map(field -> field.getColumn())
				.toList();

		assertFalse(mappedColumns.contains("weekly_duration_target_minutes"));
		assertFalse(mappedColumns.contains("weekly_training_count_target"));
	}

	@Test
	void returnsDefaultsForNullableStoredTargets() {
		UUID userId = UUID.randomUUID();
		UserPreferenceMapper mapper = mock(UserPreferenceMapper.class);
		UserPreferenceEntity entity = new UserPreferenceEntity();
		entity.setUserId(userId);
		entity.setPreferences("{\"translation_enabled\":true}");
		when(mapper.selectById(userId)).thenReturn(entity);

		var goals = new WeeklyLearningGoalRepository(mapper, objectMapper)
				.findByUserId(userId)
				.orElseThrow();

		assertEquals(120, goals.durationTargetMinutes());
		assertEquals(5, goals.trainingCountTarget());
	}

	@Test
	void returnsEmptyWhenPreferenceRowDoesNotExist() {
		UUID userId = UUID.randomUUID();
		UserPreferenceMapper mapper = mock(UserPreferenceMapper.class);
		when(mapper.selectById(userId)).thenReturn(null);

		assertTrue(new WeeklyLearningGoalRepository(mapper, objectMapper)
				.findByUserId(userId)
				.isEmpty());
	}

	@Test
	void insertsGoalsWithoutRequiringOtherPreferenceFields() {
		UUID userId = UUID.randomUUID();
		UserPreferenceMapper mapper = mock(UserPreferenceMapper.class);
		when(mapper.selectById(userId)).thenReturn(null);
		when(mapper.insert(any(UserPreferenceEntity.class))).thenReturn(1);
		WeeklyLearningGoals goals = new WeeklyLearningGoals(180, 6);

		new WeeklyLearningGoalRepository(mapper, objectMapper).save(userId, goals);

		ArgumentCaptor<UserPreferenceEntity> captor =
				ArgumentCaptor.forClass(UserPreferenceEntity.class);
		verify(mapper).insert(captor.capture());
		assertEquals(userId, captor.getValue().getUserId());
		assertEquals(180, captor.getValue().getWeeklyDurationTargetMinutes());
		assertEquals(6, captor.getValue().getWeeklyTrainingCountTarget());
		assertEquals(
				180,
				objectMapper.readTree(captor.getValue().getPreferences())
						.get("weekly_duration_target_minutes")
						.intValue());
	}

	@Test
	void updatesExistingPreferenceRowAndTranslatesFailures() {
		UUID userId = UUID.randomUUID();
		UserPreferenceMapper mapper = mock(UserPreferenceMapper.class);
		UserPreferenceEntity entity = new UserPreferenceEntity();
		entity.setUserId(userId);
		entity.setPreferences("{\"translation_enabled\":true}");
		when(mapper.selectById(userId)).thenReturn(entity);
		when(mapper.updateById(entity)).thenReturn(1);
		WeeklyLearningGoalRepository repository =
				new WeeklyLearningGoalRepository(mapper, objectMapper);

		repository.save(userId, new WeeklyLearningGoals(240, 8));

		assertEquals(240, entity.getWeeklyDurationTargetMinutes());
		assertEquals(8, entity.getWeeklyTrainingCountTarget());
		assertTrue(objectMapper.readTree(entity.getPreferences())
				.get("translation_enabled")
				.booleanValue());
		verify(mapper).updateById(entity);

		when(mapper.selectById(userId))
				.thenThrow(new IllegalStateException("select failed"));
		BusinessException failure = assertThrows(
				BusinessException.class,
				() -> repository.findByUserId(userId));
		assertEquals("PROFILE_GOALS_PERSISTENCE_FAILED", failure.code());
	}
}
