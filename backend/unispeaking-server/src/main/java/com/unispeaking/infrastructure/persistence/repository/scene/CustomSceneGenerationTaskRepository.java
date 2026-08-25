package com.unispeaking.infrastructure.persistence.repository.scene;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.persistence.typehandler.PostgresJsonbStringTypeHandler;
import com.unispeaking.domain.po.scene.CustomSceneGenerationTask;
import com.unispeaking.domain.vo.task.AsyncTaskStatus;
import com.unispeaking.infrastructure.persistence.entity.scene.CustomSceneGenerationTaskEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.CustomSceneGenerationTaskMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CustomSceneGenerationTaskRepository {
	private static final String JSONB_PARAMETER_MAPPING =
			"jdbcType=OTHER,typeHandler=" + PostgresJsonbStringTypeHandler.class.getName();

	private final CustomSceneGenerationTaskMapper mapper;

	public CustomSceneGenerationTaskRepository(CustomSceneGenerationTaskMapper mapper) {
		this.mapper = mapper;
	}

	public void create(CustomSceneGenerationTask task) {
		try {
			if (mapper.insert(toEntity(task)) != 1) throw persistenceFailure();
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<CustomSceneGenerationTask> findById(UUID taskId) {
		try {
			CustomSceneGenerationTaskEntity entity = mapper.selectById(taskId.toString());
			return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public void markCompleted(UUID taskId, String resultJson) {
		updateTerminal(taskId, AsyncTaskStatus.COMPLETED, resultJson, null);
	}

	public void markFailed(UUID taskId, String failureReason) {
		updateTerminal(taskId, AsyncTaskStatus.FAILED, null, failureReason);
	}

	public List<CustomSceneGenerationTask> findProcessingBefore(OffsetDateTime cutoff) {
		try {
			return mapper.selectList(new LambdaQueryWrapper<CustomSceneGenerationTaskEntity>()
						.eq(CustomSceneGenerationTaskEntity::getStatus, AsyncTaskStatus.PROCESSING.name())
						.lt(CustomSceneGenerationTaskEntity::getUpdatedAt, cutoff))
					.stream()
					.map(this::toDomain)
					.toList();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private void updateTerminal(
			UUID taskId,
			AsyncTaskStatus status,
			String resultJson,
			String failureReason) {
		try {
			int updated = mapper.update(
					null,
					new LambdaUpdateWrapper<CustomSceneGenerationTaskEntity>()
							.eq(CustomSceneGenerationTaskEntity::getTaskId, taskId.toString())
							.eq(CustomSceneGenerationTaskEntity::getStatus, AsyncTaskStatus.PROCESSING.name())
							.set(CustomSceneGenerationTaskEntity::getStatus, status.name())
								.set(
										CustomSceneGenerationTaskEntity::getResultJson,
										resultJson,
										JSONB_PARAMETER_MAPPING)
							.set(CustomSceneGenerationTaskEntity::getFailureReason, failureReason)
							.set(CustomSceneGenerationTaskEntity::getUpdatedAt, OffsetDateTime.now()));
			if (updated != 1) throw persistenceFailure();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private CustomSceneGenerationTaskEntity toEntity(CustomSceneGenerationTask task) {
		CustomSceneGenerationTaskEntity entity = new CustomSceneGenerationTaskEntity();
		entity.setTaskId(task.taskId().toString());
		entity.setUserId(UUID.fromString(task.userId()));
		entity.setSceneId(task.sceneId());
		entity.setSceneInput(task.sceneInput());
		entity.setUserPreference(task.userPreference());
		entity.setStatus(task.status().name());
		entity.setResultJson(task.resultJson());
		entity.setFailureReason(task.failureReason());
		entity.setCreatedAt(task.createdAt());
		entity.setUpdatedAt(task.updatedAt());
		return entity;
	}

	private CustomSceneGenerationTask toDomain(CustomSceneGenerationTaskEntity entity) {
		return new CustomSceneGenerationTask(
				UUID.fromString(entity.getTaskId()),
				entity.getUserId().toString(),
				entity.getSceneId(),
				entity.getSceneInput(),
				entity.getUserPreference(),
				AsyncTaskStatus.valueOf(entity.getStatus()),
				entity.getResultJson(),
				entity.getFailureReason(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private BusinessException persistenceFailure() {
		return new BusinessException(
				"CUSTOM_SCENE_GENERATION_TASK_PERSISTENCE_FAILED",
				"自定义场景生成任务保存失败");
	}
}
