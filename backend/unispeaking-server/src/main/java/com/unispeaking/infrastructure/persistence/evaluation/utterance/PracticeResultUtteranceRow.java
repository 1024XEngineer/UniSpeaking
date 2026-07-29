package com.unispeaking.infrastructure.persistence.evaluation.utterance;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * MyBatis 与 {@code practice_result_utterances} 之间传递的行模型。
 *
 * <p>pronunciationDetailsJson 使用文本承载 JSONB，避免依赖通用 JSON
 * TypeHandler 与项目 Jackson 版本的兼容性。</p>
 */
public record PracticeResultUtteranceRow(
		UUID id,
		UUID sessionId,
		int utteranceNo,
		String transcript,
		String aiText,
		BigDecimal overallScore,
		BigDecimal rhythmScore,
		BigDecimal toneScore,
		BigDecimal integrityScore,
		BigDecimal pronunciationScore,
		BigDecimal fluencyScore,
		String feedbackSummary,
		String suggestedExpression,
		String pronunciationDetailsJson) {

	/**
	 * 尽早拒绝无法安全映射的数据库行；详细字段约束由业务模型和 JSONB
	 * 编解码器负责。
	 */
	public PracticeResultUtteranceRow {
		Objects.requireNonNull(id, "id must not be null");
		Objects.requireNonNull(sessionId, "sessionId must not be null");
		if (utteranceNo < 1) {
			throw new IllegalArgumentException(
					"utteranceNo must be at least 1");
		}
		requirePresent(transcript, "transcript");
		Objects.requireNonNull(overallScore, "overallScore must not be null");
		Objects.requireNonNull(rhythmScore, "rhythmScore must not be null");
		Objects.requireNonNull(
				integrityScore,
				"integrityScore must not be null");
		Objects.requireNonNull(
				pronunciationScore,
				"pronunciationScore must not be null");
		Objects.requireNonNull(fluencyScore, "fluencyScore must not be null");
		requirePresent(feedbackSummary, "feedbackSummary");
		Objects.requireNonNull(
				suggestedExpression,
				"suggestedExpression must not be null");
		requirePresent(
				pronunciationDetailsJson,
				"pronunciationDetailsJson");
	}

	private static void requirePresent(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					fieldName + " must not be blank");
		}
	}
}
