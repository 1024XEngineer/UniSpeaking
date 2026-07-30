package com.unispeaking.domain.dto.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 对外返回的单词发音评分及其音素明细。
 *
 * @param word 被评分的单词
 * @param wordScore 单词发音分，不代表包含完整度等维度的综合分
 * @param phonemes 按发音顺序排列的音素评分
 */
public record WordPronunciationScore(
		String word,
		BigDecimal wordScore,
		List<PhonemeScore> phonemes) {

	/**
	 * 保存音素列表的不可变快照，防止响应构造完成后被调用方修改。
	 */
	public WordPronunciationScore {
		word = Objects.requireNonNull(word, "word must not be null");
		wordScore = Objects.requireNonNull(
				wordScore,
				"wordScore must not be null");
		phonemes = List.copyOf(
				Objects.requireNonNull(phonemes, "phonemes must not be null"));
	}
}
