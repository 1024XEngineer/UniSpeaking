package com.unispeaking.domain.dto.evaluation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 对外返回的单个音素发音评分。
 *
 * @param expectedPhoneme 标准音素
 * @param actualPhoneme 用户实际发出的音素
 * @param score 音素发音分
 */
public record PhonemeScore(
		String expectedPhoneme,
		String actualPhoneme,
		BigDecimal score) {

	/**
	 * 音素评分必须包含标准音素、实际音素和数值分数，避免向调用方返回不完整明细。
	 */
	public PhonemeScore {
		Objects.requireNonNull(expectedPhoneme, "expectedPhoneme must not be null");
		Objects.requireNonNull(actualPhoneme, "actualPhoneme must not be null");
		Objects.requireNonNull(score, "score must not be null");
	}
}
