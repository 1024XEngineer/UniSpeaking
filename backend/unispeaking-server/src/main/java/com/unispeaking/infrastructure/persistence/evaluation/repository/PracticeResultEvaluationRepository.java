package com.unispeaking.infrastructure.persistence.evaluation.repository;

import com.unispeaking.infrastructure.persistence.evaluation.mapper.PracticeResultEvaluationMapper;
import com.unispeaking.infrastructure.persistence.evaluation.result.PracticeResultScores;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 为评分处理器提供整场分数的持久化访问边界。
 */
@Repository
@Profile("!test")
public final class PracticeResultEvaluationRepository {

	private final PracticeResultEvaluationMapper mapper;

	/**
	 * 创建只依赖评分模块 Mapper 的整场评分仓储。
	 */
	public PracticeResultEvaluationRepository(
			PracticeResultEvaluationMapper mapper) {
		this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
	}

	/**
	 * 新增或覆盖一场练习的完整分数。
	 */
	public void upsert(PracticeResultScores result) {
		if (result == null) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		final int affectedRows;
		try {
			affectedRows = mapper.upsert(result);
		}
		catch (RuntimeException exception) {
			throw new EvaluationException(
					EvaluationErrorCode.PERSISTENCE_FAILED);
		}
		if (affectedRows != 1) {
			throw new EvaluationException(
					EvaluationErrorCode.PERSISTENCE_FAILED);
		}
	}

	/**
	 * 按会话标识查询整场分数；不存在时返回空 Optional。
	 */
	public Optional<PracticeResultScores> findBySessionId(UUID sessionId) {
		if (sessionId == null) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		try {
			return Optional.ofNullable(mapper.findBySessionId(sessionId));
		}
		catch (RuntimeException exception) {
			throw new EvaluationException(
					EvaluationErrorCode.PERSISTENCE_FAILED);
		}
	}
}
