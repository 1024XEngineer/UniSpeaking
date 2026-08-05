package com.unispeaking.common.prompt.evaluation;

import com.unispeaking.common.exception.evaluation.EvaluationErrorCode;
import com.unispeaking.common.exception.evaluation.EvaluationException;
import com.unispeaking.domain.vo.scene.RecommendedExpression;
import java.util.List;

/**
 * 构造单轮语言评价 Prompt 所需的评分模块输入。
 *
 * <p>practiceMode 保持开放字符串，以支持后续 IELTS、面试等专业练习类型，
 * 不在 Prompt 模块转换为固定枚举。</p>
 *
 * @param practiceMode 上游提供的练习类型标识
 * @param background 练习背景，允许为空
 * @param aiRole AI 在当前练习中的角色，允许为空
 * @param userRole 用户在当前练习中的角色，允许为空
 * @param learningGoal 当前练习的学习目标，允许为空
 * @param previousTurns 当前轮次之前的历史用户气泡
 * @param aiText 当前用户回答所对应的上一条 AI 发言，允许为空
 * @param currentTranscript 当前需要评价的用户英文原文
 */
public record DialogueTurnEvaluationPromptInput(
		String practiceMode,
		String background,
		String aiRole,
		String userRole,
		String learningGoal,
		List<DialogueTurnEvaluationHistory> previousTurns,
		String aiText,
		String currentTranscript,
		List<RecommendedExpression> recommendedExpressions) {

	public DialogueTurnEvaluationPromptInput(
			String practiceMode,
			String background,
			String aiRole,
			String userRole,
			String learningGoal,
			List<DialogueTurnEvaluationHistory> previousTurns,
			String aiText,
			String currentTranscript) {
		this(
				practiceMode,
				background,
				aiRole,
				userRole,
				learningGoal,
				previousTurns,
				aiText,
				currentTranscript,
				List.of());
	}

	/**
	 * 规范可选上下文并保存历史列表的不可变快照。
	 */
	public DialogueTurnEvaluationPromptInput {
		if (practiceMode == null || practiceMode.isBlank()) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		if (currentTranscript == null || currentTranscript.isBlank()) {
			throw new EvaluationException(
					EvaluationErrorCode.TRANSCRIPT_REQUIRED);
		}
		if (previousTurns == null || previousTurns.stream().anyMatch(
				turn -> turn == null)) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}
		if (recommendedExpressions == null
				|| recommendedExpressions.stream().anyMatch(item -> item == null)) {
			throw new EvaluationException(EvaluationErrorCode.INVALID_REQUEST);
		}

		practiceMode = practiceMode.trim();
		background = normalizeOptionalText(background);
		aiRole = normalizeOptionalText(aiRole);
		userRole = normalizeOptionalText(userRole);
		learningGoal = normalizeOptionalText(learningGoal);
		previousTurns = List.copyOf(previousTurns);
		aiText = normalizeOptionalText(aiText);
		recommendedExpressions = List.copyOf(recommendedExpressions);
	}

	private static String normalizeOptionalText(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		return text.trim();
	}
}
