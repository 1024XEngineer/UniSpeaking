package com.unispeaking.infrastructure.persistence.repository.scene;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.scene.InterviewRecord;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.infrastructure.persistence.codec.scene.InterviewJsonbCodec;
import com.unispeaking.infrastructure.persistence.entity.scene.InterviewEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.InterviewMapper;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class InterviewRepository {

	private final InterviewMapper mapper;
	private final InterviewJsonbCodec jsonbCodec;

	public InterviewRepository(
			InterviewMapper mapper,
			InterviewJsonbCodec jsonbCodec) {
		this.mapper = mapper;
		this.jsonbCodec = jsonbCodec;
	}

	public void create(InterviewRecord record) {
		try {
			if (mapper.insert(toEntity(record)) != 1) {
				throw persistenceFailure();
			}
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<InterviewRecord> findById(String interviewId) {
		return findOne(new LambdaQueryWrapper<InterviewEntity>()
				.eq(InterviewEntity::getId, interviewId));
	}

	public Optional<InterviewRecord> findByIdAndUserId(
			String interviewId,
			UUID userId) {
		return findOne(new LambdaQueryWrapper<InterviewEntity>()
				.eq(InterviewEntity::getId, interviewId)
				.eq(InterviewEntity::getUserId, userId));
	}

	public void completeAssetMetadata(
			String interviewId,
			String recordingObjectKey,
			int recordingDurationSeconds,
			OffsetDateTime completedAt) {
		try {
			int updated = mapper.update(
					null,
					new LambdaUpdateWrapper<InterviewEntity>()
							.eq(InterviewEntity::getId, interviewId)
							.set(
									InterviewEntity::getRecordingObjectKey,
									recordingObjectKey)
							.set(
									InterviewEntity::getRecordingDurationSeconds,
									recordingDurationSeconds)
							.set(
									InterviewEntity::getCompletedAt,
									completedAt)
							.set(
									InterviewEntity::getUpdatedAt,
									completedAt));
			if (updated != 1) {
				throw persistenceFailure();
			}
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public int deleteById(String interviewId) {
		try {
			return mapper.delete(new LambdaQueryWrapper<InterviewEntity>()
					.eq(InterviewEntity::getId, interviewId));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private Optional<InterviewRecord> findOne(
			LambdaQueryWrapper<InterviewEntity> query) {
		try {
			InterviewEntity entity = mapper.selectOne(query);
			return entity == null
					? Optional.empty()
					: Optional.of(toDomain(entity));
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private InterviewEntity toEntity(InterviewRecord record) {
		InterviewEntity entity = new InterviewEntity();
		entity.setId(record.id());
		entity.setUserId(record.userId());
		entity.setSessionId(record.sessionId());
		entity.setJobTitle(record.jobTitle());
		entity.setDifficulty(record.difficulty().name());
		entity.setRoleSummary(jsonbCodec.encodeRoleSummary(record.roleSummary()));
		entity.setRecordingObjectKey(record.recordingObjectKey());
		entity.setRecordingDurationSeconds(record.recordingDurationSeconds());
		entity.setCompletedAt(record.completedAt());
		entity.setCreatedAt(record.createdAt());
		entity.setUpdatedAt(record.updatedAt());
		return entity;
	}

	private InterviewRecord toDomain(InterviewEntity entity) {
		return new InterviewRecord(
				entity.getId(),
				entity.getUserId(),
				entity.getSessionId(),
				entity.getJobTitle(),
				InterviewDifficulty.valueOf(entity.getDifficulty()),
				jsonbCodec.decodeRoleSummary(entity.getRoleSummary()),
				entity.getRecordingObjectKey(),
				entity.getRecordingDurationSeconds(),
				entity.getCompletedAt(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private BusinessException persistenceFailure() {
		return new BusinessException(
				"INTERVIEW_PERSISTENCE_FAILED",
				"Interview persistence operation failed");
	}
}
