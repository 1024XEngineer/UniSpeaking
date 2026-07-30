package com.unispeaking.infrastructure.persistence.evaluation.query;

import com.unispeaking.infrastructure.persistence.evaluation.repository.PracticeResultEvaluationRepository;
import com.unispeaking.infrastructure.persistence.evaluation.repository.PracticeResultFeedbackRepository;
import com.unispeaking.infrastructure.persistence.evaluation.repository.PracticeResultUtteranceRepository;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在一致的只读事务中组合整场分数、反馈和全部气泡。
 *
 * <p>本组件只负责持久化组合，不判断报告不存在或不完整；对应业务错误由
 * 整场查询处理器根据两个 Optional 的组合状态决定。</p>
 */
@Component
@Profile("!test")
public class ConversationEvaluationPersistenceQuery {

	private final PracticeResultEvaluationRepository resultRepository;
	private final PracticeResultFeedbackRepository feedbackRepository;
	private final PracticeResultUtteranceRepository utteranceRepository;

	/**
	 * 创建整场持久化组合查询。
	 */
	public ConversationEvaluationPersistenceQuery(
			PracticeResultEvaluationRepository resultRepository,
			PracticeResultFeedbackRepository feedbackRepository,
			PracticeResultUtteranceRepository utteranceRepository) {
		this.resultRepository = Objects.requireNonNull(
				resultRepository,
				"resultRepository must not be null");
		this.feedbackRepository = Objects.requireNonNull(
				feedbackRepository,
				"feedbackRepository must not be null");
		this.utteranceRepository = Objects.requireNonNull(
				utteranceRepository,
				"utteranceRepository must not be null");
	}

	/**
	 * 查询整场评分持久化快照。
	 *
	 * <p>{@code REPEATABLE_READ} 保证三次读取观察到同一数据库版本，避免报告
	 * 覆盖期间组合出不同版本的分数和反馈。</p>
	 */
	@Transactional(
			readOnly = true,
			isolation = Isolation.REPEATABLE_READ)
	public ConversationEvaluationPersistenceSnapshot findBySessionId(
			UUID sessionId) {
		if (sessionId == null) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}

		return new ConversationEvaluationPersistenceSnapshot(
				resultRepository.findBySessionId(sessionId),
				feedbackRepository.findBySessionId(sessionId),
				utteranceRepository.findAll(sessionId));
	}
}
