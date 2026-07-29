package com.unispeaking.infrastructure.persistence.evaluation.repository;

import com.unispeaking.infrastructure.persistence.evaluation.feedback.PracticeResultFeedback;
import com.unispeaking.infrastructure.persistence.evaluation.feedback.PracticeResultFeedbackRow;
import com.unispeaking.infrastructure.persistence.evaluation.mapper.PracticeResultFeedbackMapper;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * 为整场报告提供文字反馈的持久化边界。
 *
 * <p>仓储负责不可变列表与 PostgreSQL 数组投影的转换，并隐藏 Mapper 和
 * 数据映射异常。仅在非测试环境注册，测试使用可控 Mapper stub。</p>
 */
@Repository
@Profile("!test")
public final class PracticeResultFeedbackRepository {

	private final PracticeResultFeedbackMapper mapper;

	/**
	 * 创建只依赖整场反馈 Mapper 的仓储。
	 */
	public PracticeResultFeedbackRepository(
			PracticeResultFeedbackMapper mapper) {
		this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
	}

	/**
	 * 新增或覆盖一场练习的完整反馈。
	 *
	 * <p>正常 PostgreSQL INSERT 或 ON CONFLICT UPDATE 都应影响一行，其他
	 * 行数按持久化异常处理。</p>
	 */
	public void upsert(PracticeResultFeedback feedback) {
		if (feedback == null) {
			throw invalidRequest();
		}

		int affectedRows;
		try {
			affectedRows = mapper.upsert(toRow(feedback));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
		if (affectedRows != 1) {
			throw persistenceFailure();
		}
	}

	/**
	 * 查询一场练习的整场反馈；不存在时返回空 Optional。
	 */
	public Optional<PracticeResultFeedback> findBySessionId(UUID sessionId) {
		if (sessionId == null) {
			throw invalidRequest();
		}

		try {
			return Optional.ofNullable(mapper.findBySessionId(sessionId))
					.map(this::toFeedback);
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private PracticeResultFeedbackRow toRow(
			PracticeResultFeedback feedback) {
		return new PracticeResultFeedbackRow(
				feedback.sessionId(),
				feedback.summary(),
				feedback.strengths().toArray(String[]::new),
				feedback.improvements().toArray(String[]::new));
	}

	private PracticeResultFeedback toFeedback(
			PracticeResultFeedbackRow row) {
		return new PracticeResultFeedback(
				row.sessionId(),
				row.summary(),
				Arrays.asList(row.strengths()),
				Arrays.asList(row.improvements()));
	}

	private EvaluationException invalidRequest() {
		return new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
	}

	private EvaluationException persistenceFailure() {
		return new EvaluationException(EvaluationErrorCode.PERSISTENCE_FAILED);
	}
}
