package com.unispeaking.domain.dto.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 单轮对话评分的公开结果。
 *
 * <p>分数范围及 Provider 响应完整性由评分处理器校验，本类型只负责稳定承载公开数据。</p>
 *
 * @param turnNo 用户气泡在会话中的顺序号
 * @param transcript 用户回答的转写文本
 * @param overallScore 单轮综合分
 * @param rhythmScore 节奏分
 * @param toneScore 语调分
 * @param integrityScore 完整度分
 * @param pronunciationScore 发音准确度分
 * @param fluencyScore 流利度分
 * @param feedbackSummary 中文反馈摘要
 * @param suggestedExpression 建议使用的英文表达
 * @param words 按朗读顺序排列的单词发音评分
 */
public record DialogueTurnEvaluationResult(
		Integer turnNo,
		String transcript,
		BigDecimal overallScore,
		BigDecimal rhythmScore,
		BigDecimal toneScore,
		BigDecimal integrityScore,
		BigDecimal pronunciationScore,
		BigDecimal fluencyScore,
		String feedbackSummary,
		String suggestedExpression,
		List<WordPronunciationScore> words) {

	/**
	 * 保存单词评分列表的不可变快照；过短回答应显式传入空列表。
	 */
	public DialogueTurnEvaluationResult {
		turnNo = Objects.requireNonNull(turnNo, "turnNo must not be null");
		transcript = Objects.requireNonNull(
				transcript,
				"transcript must not be null");
		words = List.copyOf(
				Objects.requireNonNull(words, "words must not be null"));
	}
}
