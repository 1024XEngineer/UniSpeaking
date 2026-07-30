package com.unispeaking.infrastructure.persistence.evaluation.repository;

import com.unispeaking.infrastructure.persistence.evaluation.mapper.PracticeSessionEvaluationMapper;
import com.unispeaking.infrastructure.persistence.evaluation.session.PracticeSessionEvaluationContext;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 为评分处理器提供练习会话上下文的只读访问边界。
 *
 * <p>仅在非测试环境注册，与评分 Mapper 的 Profile 保持一致，兼容测试环境
 * 整体关闭数据库连接的配置。</p>
 */
@Repository
@Profile("!test")
public final class PracticeSessionEvaluationRepository {

	private final PracticeSessionEvaluationMapper mapper;

	/**
	 * 创建只依赖评分模块 Mapper 的会话仓储。
	 */
	public PracticeSessionEvaluationRepository(
			PracticeSessionEvaluationMapper mapper) {
		this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
	}

	/**
	 * 按 UUID 查询评分上下文；不存在时返回空 Optional。
	 *
	 * <p>数据库或映射异常统一转换为安全持久化错误，不暴露 SQL、连接信息或
	 * 底层异常文本。</p>
	 */
	public Optional<PracticeSessionEvaluationContext> findBySessionId(
			UUID sessionId) {
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
