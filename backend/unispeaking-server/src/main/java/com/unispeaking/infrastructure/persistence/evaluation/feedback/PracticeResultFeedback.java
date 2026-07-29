package com.unispeaking.infrastructure.persistence.evaluation.feedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 一场练习的整场文字反馈。
 *
 * @param sessionId 练习会话标识
 * @param summary 整场表现总结
 * @param strengths 一至三项表现优势
 * @param improvements 一至三项改进建议
 */
public record PracticeResultFeedback(
		UUID sessionId,
		String summary,
		List<String> strengths,
		List<String> improvements) {

	private static final int MIN_ITEM_COUNT = 1;
	private static final int MAX_ITEM_COUNT = 3;

	/**
	 * 校验反馈完整性、统一文本首尾空白，并保存不可变列表副本。
	 */
	public PracticeResultFeedback {
		sessionId = Objects.requireNonNull(
				sessionId,
				"sessionId must not be null");
		summary = requireText(summary, "summary");
		strengths = normalizeItems(strengths, "strengths");
		improvements = normalizeItems(improvements, "improvements");
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					fieldName + " must not be blank");
		}
		return value.trim();
	}

	private static List<String> normalizeItems(
			List<String> values,
			String fieldName) {
		Objects.requireNonNull(values, fieldName + " must not be null");
		if (values.size() < MIN_ITEM_COUNT
				|| values.size() > MAX_ITEM_COUNT) {
			throw new IllegalArgumentException(
					fieldName + " must contain between 1 and 3 items");
		}

		List<String> normalized = new ArrayList<>(values.size());
		for (String value : values) {
			normalized.add(requireText(value, fieldName + " item"));
		}
		return List.copyOf(normalized);
	}
}
