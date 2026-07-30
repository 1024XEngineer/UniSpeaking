package com.unispeaking.infrastructure.persistence.evaluation.feedback;

import java.util.Objects;
import java.util.UUID;

/**
 * {@code practice_result_feedbacks} 的 MyBatis 行投影。
 *
 * <p>数据库数组使用 {@code String[]} 与 JDBC TypeHandler 对接；构造和访问
 * 均执行防御性复制，防止可变数组越过持久化边界。</p>
 *
 * @param sessionId 练习会话标识
 * @param summary 整场表现总结
 * @param strengths 表现优势数据库数组
 * @param improvements 改进建议数据库数组
 */
public record PracticeResultFeedbackRow(
		UUID sessionId,
		String summary,
		String[] strengths,
		String[] improvements) {

	/**
	 * 保证数据库投影的必填字段存在，并隔离调用方数组。
	 */
	public PracticeResultFeedbackRow {
		sessionId = Objects.requireNonNull(
				sessionId,
				"sessionId must not be null");
		summary = Objects.requireNonNull(summary, "summary must not be null");
		strengths = Objects.requireNonNull(
				strengths,
				"strengths must not be null").clone();
		improvements = Objects.requireNonNull(
				improvements,
				"improvements must not be null").clone();
	}

	/**
	 * 返回表现优势的副本，避免外部修改记录内部数组。
	 */
	@Override
	public String[] strengths() {
		return strengths.clone();
	}

	/**
	 * 返回改进建议的副本，避免外部修改记录内部数组。
	 */
	@Override
	public String[] improvements() {
		return improvements.clone();
	}
}
