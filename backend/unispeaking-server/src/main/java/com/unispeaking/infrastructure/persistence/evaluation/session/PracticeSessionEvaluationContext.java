package com.unispeaking.infrastructure.persistence.evaluation.session;

import java.util.Objects;
import java.util.UUID;

/**
 * 评分模块从练习会话及其可选场景读取的上下文。
 *
 * <p>practiceMode 和 status 保持数据库字符串，不转换为当前业务枚举，从而
 * 兼容后续 IELTS、面试等专业练习类型。自由对话没有场景时，四个场景字段
 * 均允许为 {@code null}。</p>
 *
 * @param sessionId 练习会话标识
 * @param userId 会话所属用户
 * @param practiceMode 开放的练习类型标识
 * @param status 数据库中的会话状态
 * @param background 场景背景，允许为空
 * @param aiRole AI 角色，允许为空
 * @param userRole 用户角色，允许为空
 * @param learningGoal 学习目标，允许为空
 */
public record PracticeSessionEvaluationContext(
		UUID sessionId,
		UUID userId,
		String practiceMode,
		String status,
		String background,
		String aiRole,
		String userRole,
		String learningGoal) {

	/**
	 * 校验会话必要字段，并统一可选场景文本的空值语义。
	 */
	public PracticeSessionEvaluationContext {
		sessionId = Objects.requireNonNull(
				sessionId,
				"sessionId must not be null");
		userId = Objects.requireNonNull(userId, "userId must not be null");
		practiceMode = requireText(practiceMode, "practiceMode").trim();
		status = requireText(status, "status").trim();
		background = normalizeOptionalText(background);
		aiRole = normalizeOptionalText(aiRole);
		userRole = normalizeOptionalText(userRole);
		learningGoal = normalizeOptionalText(learningGoal);
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					fieldName + " must not be blank");
		}
		return value;
	}

	private static String normalizeOptionalText(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
