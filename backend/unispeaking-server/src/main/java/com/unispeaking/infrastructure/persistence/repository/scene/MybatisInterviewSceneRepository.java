package com.unispeaking.infrastructure.persistence.repository.scene;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.InterviewErrorCode;
import com.unispeaking.domain.po.scene.InterviewSceneDefinition;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.infrastructure.persistence.entity.scene.InterviewSceneEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.InterviewSceneMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class MybatisInterviewSceneRepository implements InterviewSceneRepository {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			MybatisInterviewSceneRepository.class);

	private final InterviewSceneMapper sceneMapper;

	public MybatisInterviewSceneRepository(InterviewSceneMapper sceneMapper) {
		this.sceneMapper = sceneMapper;
	}

	@Override
	public void save(InterviewSceneDefinition definition) {
		try {
			sceneMapper.insert(toEntity(definition));
			LOGGER.info(
					"interview scene persisted sceneId={} userId={}",
					definition.sceneId(),
					definition.userId());
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	@Override
	public Optional<InterviewSceneDefinition> findById(String sceneId) {
		InterviewSceneEntity entity = sceneMapper.selectById(sceneId);
		if (entity == null || entity.getDeletedAt() != null) {
			return Optional.empty();
		}
		return Optional.of(toDefinition(entity));
	}

	@Override
	public List<InterviewSceneDefinition> findByUserId(String userId) {
		UUID ownerId;
		try {
			ownerId = UUID.fromString(userId);
		}
		catch (IllegalArgumentException exception) {
			return List.of();
		}
		return sceneMapper.selectList(new LambdaQueryWrapper<InterviewSceneEntity>()
						.eq(InterviewSceneEntity::getUserId, ownerId)
						.isNull(InterviewSceneEntity::getDeletedAt)
						.orderByDesc(InterviewSceneEntity::getUpdatedAt))
				.stream()
				.map(this::toDefinition)
				.toList();
	}

	@Override
	public Optional<InterviewSceneDefinition> findOwnedById(
			String sceneId,
			String userId) {
		UUID ownerId;
		try {
			ownerId = UUID.fromString(userId);
		}
		catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
		InterviewSceneEntity entity = sceneMapper.selectOne(
				new LambdaQueryWrapper<InterviewSceneEntity>()
						.eq(InterviewSceneEntity::getSceneId, sceneId)
						.eq(InterviewSceneEntity::getUserId, ownerId)
						.isNull(InterviewSceneEntity::getDeletedAt));
		if (entity == null) {
			return Optional.empty();
		}
		return Optional.of(toDefinition(entity));
	}

	@Override
	public boolean softDelete(String sceneId, String userId) {
		UUID ownerId;
		try {
			ownerId = UUID.fromString(userId);
		}
		catch (IllegalArgumentException exception) {
			return false;
		}
		try {
			return sceneMapper.update(
					null,
					new LambdaUpdateWrapper<InterviewSceneEntity>()
							.eq(InterviewSceneEntity::getSceneId, sceneId)
							.eq(InterviewSceneEntity::getUserId, ownerId)
							.isNull(InterviewSceneEntity::getDeletedAt)
							.set(
									InterviewSceneEntity::getDeletedAt,
									OffsetDateTime.now())) == 1;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure(exception);
		}
	}

	private InterviewSceneEntity toEntity(InterviewSceneDefinition definition) {
		InterviewSceneEntity entity = new InterviewSceneEntity();
		entity.setSceneId(definition.sceneId());
		entity.setUserId(UUID.fromString(definition.userId()));
		entity.setConfirmedMaterial(definition.confirmedMaterialJson());
		entity.setFinalText(definition.finalText());
		entity.setInterviewContext(definition.interviewContextJson());
		entity.setDifficulty(definition.difficulty() == null
				? null
				: definition.difficulty().name());
		entity.setScenePrompt(definition.scenePrompt());
		entity.setCreatedAt(definition.createdAt());
		entity.setUpdatedAt(definition.updatedAt());
		entity.setDeletedAt(definition.deletedAt());
		return entity;
	}

	private InterviewSceneDefinition toDefinition(InterviewSceneEntity entity) {
		return new InterviewSceneDefinition(
				entity.getSceneId(),
				entity.getUserId().toString(),
				entity.getConfirmedMaterial(),
				entity.getFinalText(),
				entity.getInterviewContext(),
				entity.getDifficulty() == null
						? null
						: InterviewDifficulty.valueOf(entity.getDifficulty()),
				entity.getScenePrompt(),
				entity.getCreatedAt(),
				entity.getUpdatedAt(),
				entity.getDeletedAt());
	}

	private BusinessException persistenceFailure(Throwable cause) {
		return new BusinessException(
				InterviewErrorCode.INTERVIEW_SCENE_PERSISTENCE_FAILED,
				"面试场景保存失败");
	}
}
