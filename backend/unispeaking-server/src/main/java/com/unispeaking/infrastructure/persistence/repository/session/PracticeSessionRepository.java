package com.unispeaking.infrastructure.persistence.repository.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.session.PracticeSessionRecord;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import com.unispeaking.infrastructure.persistence.entity.session.PracticeSessionEntity;
import com.unispeaking.infrastructure.persistence.mapper.session.PracticeSessionMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PracticeSessionRepository {

	private final PracticeSessionMapper mapper;

	public PracticeSessionRepository(PracticeSessionMapper mapper) {
		this.mapper = mapper;
	}

	public void create(PracticeSessionRecord record) {
		PracticeSessionEntity entity = toEntity(record);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		try {
			if (mapper.insert(entity) != 1) {
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

	public void complete(String sessionId, UUID userId, Instant endedAt) {
		updateTerminalStatus(
				sessionId,
				userId,
				SessionStatus.COMPLETED,
				endedAt);
	}

	public void fail(String sessionId, UUID userId, Instant endedAt) {
		updateTerminalStatus(
				sessionId,
				userId,
				SessionStatus.FAILED,
				endedAt);
	}

	public void bindProviderSession(
			String sessionId,
			UUID userId,
			String providerSessionId) {
		if (providerSessionId == null || providerSessionId.isBlank()) {
			throw new BusinessException(
					"PROVIDER_SESSION_ID_REQUIRED",
					"服务商会话标识不能为空");
		}
		try {
			int updated = mapper.update(
					null,
					new LambdaUpdateWrapper<PracticeSessionEntity>()
							.eq(PracticeSessionEntity::getSessionId, sessionId)
							.eq(PracticeSessionEntity::getUserId, userId)
							.set(PracticeSessionEntity::getProviderSessionId, providerSessionId)
							.set(PracticeSessionEntity::getUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)));
			if (updated != 1) throw persistenceFailure();
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<PracticeSessionRecord> findCompletedOverlapping(
			UUID userId,
			Instant start,
			Instant end) {
		try {
			return mapper.selectList(
						new LambdaQueryWrapper<PracticeSessionEntity>()
								.eq(PracticeSessionEntity::getUserId, userId)
								.eq(
										PracticeSessionEntity::getStatus,
										SessionStatus.COMPLETED.name())
								.isNotNull(PracticeSessionEntity::getEndedAt)
								.lt(
										PracticeSessionEntity::getStartedAt,
										atUtc(end))
								.gt(
										PracticeSessionEntity::getEndedAt,
										atUtc(start))
								.orderByAsc(
										PracticeSessionEntity::getStartedAt))
					.stream()
					.map(this::toDomain)
					.toList();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<PracticeSessionRecord> findCompletedByUserId(UUID userId) {
		try {
			return mapper.selectList(
						new LambdaQueryWrapper<PracticeSessionEntity>()
								.eq(PracticeSessionEntity::getUserId, userId)
								.eq(
										PracticeSessionEntity::getStatus,
										SessionStatus.COMPLETED.name())
								.isNotNull(PracticeSessionEntity::getEndedAt)
								.orderByAsc(PracticeSessionEntity::getStartedAt))
					.stream()
					.map(this::toDomain)
					.toList();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<PracticeSessionRecord> findBySceneId(String sceneId) {
		try {
			return mapper.selectList(
						new LambdaQueryWrapper<PracticeSessionEntity>()
								.eq(PracticeSessionEntity::getSceneId, sceneId)
								.orderByAsc(PracticeSessionEntity::getStartedAt))
					.stream()
					.map(this::toDomain)
					.toList();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<PracticeSessionRecord> findBySessionId(String sessionId) {
		if (sessionId == null || sessionId.isBlank()) return Optional.empty();
		try {
			return Optional.ofNullable(mapper.selectOne(
					new LambdaQueryWrapper<PracticeSessionEntity>()
							.eq(PracticeSessionEntity::getSessionId, sessionId)))
					.map(this::toDomain);
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<PracticeSessionRecord> findCompletedByUserAndSceneType(
			UUID userId,
			SceneType sceneType) {
		try {
			return mapper.selectList(
						new LambdaQueryWrapper<PracticeSessionEntity>()
								.eq(PracticeSessionEntity::getUserId, userId)
								.eq(
										PracticeSessionEntity::getSceneType,
										sceneType.name())
								.eq(
										PracticeSessionEntity::getStatus,
										SessionStatus.COMPLETED.name())
								.orderByAsc(
										PracticeSessionEntity::getStartedAt))
					.stream()
					.map(this::toDomain)
					.toList();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	/**
	 * 按 UTC 日统计指定场景类型当日 COMPLETED 会话数（配额派生计数）。
	 * 条件方向复用 {@code idx_practice_session_user_completed_at} 部分索引
	 * （user_id, ended_at DESC WHERE status='COMPLETED' AND ended_at IS NOT NULL）。
	 */
	public long countCompletedOnDate(
			UUID userId,
			SceneType sceneType,
			LocalDate date) {
		OffsetDateTime dayStart = date.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
		OffsetDateTime nextDayStart = date.plusDays(1)
				.atStartOfDay(ZoneOffset.UTC)
				.toOffsetDateTime();
		try {
			return mapper.selectCount(
					new LambdaQueryWrapper<PracticeSessionEntity>()
							.eq(PracticeSessionEntity::getUserId, userId)
							.eq(
									PracticeSessionEntity::getSceneType,
									sceneType.name())
							.eq(
									PracticeSessionEntity::getStatus,
									SessionStatus.COMPLETED.name())
							.isNotNull(PracticeSessionEntity::getEndedAt)
							.ge(PracticeSessionEntity::getEndedAt, dayStart)
							.lt(PracticeSessionEntity::getEndedAt, nextDayStart));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}
	private void updateTerminalStatus(
			String sessionId,
			UUID userId,
			SessionStatus status,
			Instant endedAt) {
		OffsetDateTime end = atUtc(endedAt);
		try {
			int updated = mapper.update(
					null,
					new LambdaUpdateWrapper<PracticeSessionEntity>()
							.eq(PracticeSessionEntity::getSessionId, sessionId)
							.eq(PracticeSessionEntity::getUserId, userId)
							.notIn(
									PracticeSessionEntity::getStatus,
									SessionStatus.COMPLETED.name(),
									SessionStatus.FAILED.name())
							.set(PracticeSessionEntity::getStatus, status.name())
							.set(PracticeSessionEntity::getEndedAt, end)
							.set(PracticeSessionEntity::getUpdatedAt, end));
			if (updated == 1 || alreadyTerminal(sessionId, userId, status)) {
				return;
			}
			throw persistenceFailure();
		}
		catch (BusinessException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private boolean alreadyTerminal(
			String sessionId,
			UUID userId,
			SessionStatus status) {
		return mapper.selectCount(
				new LambdaQueryWrapper<PracticeSessionEntity>()
						.eq(PracticeSessionEntity::getSessionId, sessionId)
						.eq(PracticeSessionEntity::getUserId, userId)
						.eq(PracticeSessionEntity::getStatus, status.name())) == 1;
	}

	private PracticeSessionEntity toEntity(PracticeSessionRecord record) {
		PracticeSessionEntity entity = new PracticeSessionEntity();
		entity.setSessionId(record.sessionId());
		entity.setUserId(record.userId());
		entity.setSceneId(record.sceneId());
		entity.setSceneType(record.sceneType().name());
		entity.setStatus(record.status().name());
		entity.setStartedAt(atUtc(record.startedAt()));
		entity.setEndedAt(record.endedAt() == null ? null : atUtc(record.endedAt()));
		entity.setProviderSessionId(record.providerSessionId());
		return entity;
	}

	private PracticeSessionRecord toDomain(PracticeSessionEntity entity) {
		return new PracticeSessionRecord(
				entity.getSessionId(),
				entity.getUserId(),
				entity.getSceneId(),
				SceneType.valueOf(entity.getSceneType()),
				SessionStatus.valueOf(entity.getStatus()),
					entity.getStartedAt().toInstant(),
					entity.getEndedAt() == null
							? null
							: entity.getEndedAt().toInstant(),
					entity.getProviderSessionId());
	}

	private OffsetDateTime atUtc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private BusinessException persistenceFailure() {
		return new BusinessException(
				"PRACTICE_SESSION_PERSISTENCE_FAILED",
				"练习会话记录保存失败");
	}
}
