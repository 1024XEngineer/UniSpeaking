package com.unispeaking.infrastructure.persistence.evaluation.query;

import com.unispeaking.infrastructure.persistence.evaluation.feedback.PracticeResultFeedback;
import com.unispeaking.infrastructure.persistence.evaluation.result.PracticeResultScores;
import com.unispeaking.infrastructure.persistence.evaluation.utterance.PracticeResultUtterance;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 整场评分查询在同一数据库快照中取得的持久化数据。
 *
 * <p>分数和反馈分别保留 {@link Optional} 状态，由上层处理器区分结果不存在
 * 与结果不完整；气泡列表同时包含正常评分和过短记录。</p>
 *
 * @param scores 整场分数，尚未生成时为空
 * @param feedback 整场文字反馈，尚未生成时为空
 * @param utterances 按气泡序号升序排列的全部单轮记录
 */
public record ConversationEvaluationPersistenceSnapshot(
		Optional<PracticeResultScores> scores,
		Optional<PracticeResultFeedback> feedback,
		List<PracticeResultUtterance> utterances) {

	/**
	 * 校验组合查询结果，并保存气泡列表的不可变快照。
	 */
	public ConversationEvaluationPersistenceSnapshot {
		scores = Objects.requireNonNull(scores, "scores must not be null");
		feedback = Objects.requireNonNull(
				feedback,
				"feedback must not be null");
		utterances = List.copyOf(Objects.requireNonNull(
				utterances,
				"utterances must not be null"));
	}
}
