package com.unispeaking.infrastructure.persistence.evaluation.asset;

import java.util.Objects;
import java.util.UUID;

/**
 * 跟读评分所需的参考句及其所属用户。
 *
 * @param sentenceId 学习资产句子主键
 * @param referenceText 供应商发音评测使用的固定参考句
 * @param userId 句子所属自定义场景的用户标识
 */
public record AssetSentenceEvaluationTarget(
		UUID sentenceId,
		String referenceText,
		UUID userId) {

	/**
	 * 保证 Provider 调用所需的句子身份、参考文本和所属用户均完整。
	 */
	public AssetSentenceEvaluationTarget {
		sentenceId = Objects.requireNonNull(
				sentenceId,
				"sentenceId must not be null");
		userId = Objects.requireNonNull(userId, "userId must not be null");
		if (referenceText == null || referenceText.isBlank()) {
			throw new IllegalArgumentException(
					"referenceText must not be blank");
		}
	}
}
