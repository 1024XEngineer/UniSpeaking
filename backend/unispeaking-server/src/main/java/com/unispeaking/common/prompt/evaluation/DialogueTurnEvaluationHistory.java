package com.unispeaking.common.prompt.evaluation;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;

/**
 * 单轮语言评价 Prompt 使用的一条历史用户气泡。
 *
 * <p>序号仅用于恢复会话顺序，不会写入最终 Prompt；每条记录按 AI 发言在前、
 * 用户 transcript 在后的顺序展开。</p>
 *
 * @param utteranceNo 历史用户气泡序号，从 1 开始
 * @param aiText 该用户气泡所回应的 AI 发言，允许为空
 * @param transcript 历史用户回答原文
 */
public record DialogueTurnEvaluationHistory(
		int utteranceNo,
		String aiText,
		String transcript) {

	/**
	 * 统一历史输入的可选文本语义，同时保留 transcript 原文。
	 */
	public DialogueTurnEvaluationHistory {
		if (utteranceNo < 1 || transcript == null || transcript.isBlank()) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		aiText = normalizeOptionalText(aiText);
	}

	private static String normalizeOptionalText(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		return text.trim();
	}
}
