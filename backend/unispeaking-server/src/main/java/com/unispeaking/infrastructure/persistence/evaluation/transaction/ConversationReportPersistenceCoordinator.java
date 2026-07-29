package com.unispeaking.infrastructure.persistence.evaluation.transaction;

import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.infrastructure.persistence.evaluation.feedback.PracticeResultFeedback;
import com.unispeaking.infrastructure.persistence.evaluation.repository.PracticeResultEvaluationRepository;
import com.unispeaking.infrastructure.persistence.evaluation.repository.PracticeResultFeedbackRepository;
import com.unispeaking.infrastructure.persistence.evaluation.result.PracticeResultScores;
import com.unispeaking.service.evaluation.internal.exception.EvaluationErrorCode;
import com.unispeaking.service.evaluation.internal.exception.EvaluationException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在同一事务中保存整场分数与文字反馈。
 *
 * <p>协调器只负责数据库写入顺序和事务边界，不在事务中调用 AI Provider。
 * 两个持久化模型会在写入前一并完成校验，避免非法报告造成部分数据更新。</p>
 */
@Component
@Profile("!test")
public class ConversationReportPersistenceCoordinator {

	private final PracticeResultEvaluationRepository evaluationRepository;
	private final PracticeResultFeedbackRepository feedbackRepository;

	/**
	 * 创建整场报告持久化协调器。
	 */
	public ConversationReportPersistenceCoordinator(
			PracticeResultEvaluationRepository evaluationRepository,
			PracticeResultFeedbackRepository feedbackRepository) {
		this.evaluationRepository = Objects.requireNonNull(
				evaluationRepository,
				"evaluationRepository must not be null");
		this.feedbackRepository = Objects.requireNonNull(
				feedbackRepository,
				"feedbackRepository must not be null");
	}

	/**
	 * 原子覆盖指定会话的整场分数和文字反馈。
	 *
	 * <p>反馈写入失败时，Spring 事务会回滚已经完成的分数写入。仓储抛出的
	 * {@link EvaluationException} 原样向上传递，由评分处理器统一响应。</p>
	 *
	 * @param sessionId 练习会话标识
	 * @param report 已完成解析和计算的整场报告
	 */
	@Transactional
	public void save(
			UUID sessionId,
			DialogueReportResult report) {
		if (sessionId == null || report == null) {
			throw invalidRequest();
		}

		final PracticeResultScores scores;
		final PracticeResultFeedback feedback;
		try {
			scores = PracticeResultScores.from(sessionId, report);
			feedback = new PracticeResultFeedback(
					sessionId,
					report.summary(),
					report.strengths(),
					report.improvements());
		}
		catch (NullPointerException | IllegalArgumentException exception) {
			throw invalidRequest();
		}

		evaluationRepository.upsert(scores);
		feedbackRepository.upsert(feedback);
	}

	private EvaluationException invalidRequest() {
		return new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
	}
}
