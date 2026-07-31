package com.unispeaking.infrastructure.persistence.entity.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * {@code turn_evaluation.pronunciation_details} 的 JSONB 投影。
 *
 * <p>六项单轮分数由表字段保存，本结构顶层只包含单词和音素明细。空 words
 * 是“过短，不予评分”的合法持久化形式。</p>
 */
@JsonPropertyOrder("words")
public record PronunciationDetailsJson(
		@JsonProperty("words")
		List<Word> words) {

	/**
	 * 保存单词列表的不可变快照；允许空列表，但不允许 null 或 null 元素。
	 */
	public PronunciationDetailsJson {
		words = List.copyOf(Objects.requireNonNull(
				words,
				"words must not be null"));
	}

	/**
	 * 单轮气泡发音 JSONB 中的单词评分。
	 */
	@JsonPropertyOrder({
		"index",
		"text",
		"pronunciation_score",
		"phonemes"
	})
	public record Word(
			@JsonProperty("index")
			int index,
			@JsonProperty("text")
			String text,
			@JsonProperty("pronunciation_score")
			BigDecimal pronunciationScore,
			@JsonProperty("phonemes")
			List<Phoneme> phonemes) {

		/**
		 * 正常评分单词必须包含发音分和至少一个完整音素。
		 */
		public Word {
			requireIndex(index);
			text = requireText(text, "text");
			pronunciationScore =
					requireScore(pronunciationScore, "pronunciationScore");
			phonemes = List.copyOf(Objects.requireNonNull(
					phonemes,
					"phonemes must not be null"));
			if (phonemes.isEmpty()) {
				throw new IllegalArgumentException(
						"phonemes must not be empty");
			}
		}
	}

	/**
	 * 单轮气泡发音 JSONB 中的完整音素评分。
	 */
	@JsonPropertyOrder({
		"index",
		"expected_phoneme",
		"actual_phoneme",
		"pronunciation_score",
		"start_position",
		"end_position"
	})
	public record Phoneme(
			@JsonProperty("index")
			int index,
			@JsonProperty("expected_phoneme")
			String expectedPhoneme,
			@JsonProperty("actual_phoneme")
			String actualPhoneme,
			@JsonProperty("pronunciation_score")
			BigDecimal pronunciationScore,
			@JsonProperty("start_position")
			int startPosition,
			@JsonProperty("end_position")
			int endPosition) {

		public Phoneme(
				int index,
				String expectedPhoneme,
				String actualPhoneme,
				BigDecimal pronunciationScore) {
			this(
					index,
					expectedPhoneme,
					actualPhoneme,
					pronunciationScore,
					0,
					1);
		}

		/**
		 * 标准音素和实际音素都必须存在，不能退化为单一 symbol 字段。
		 */
		public Phoneme {
			requireIndex(index);
			expectedPhoneme =
					requireText(expectedPhoneme, "expectedPhoneme");
			actualPhoneme = requireText(actualPhoneme, "actualPhoneme");
			pronunciationScore =
					requireScore(pronunciationScore, "pronunciationScore");
			if (startPosition < 0 || endPosition <= startPosition) {
				throw new IllegalArgumentException(
						"phoneme positions must define a positive duration");
			}
		}
	}

	private static void requireIndex(int index) {
		if (index < 0) {
			throw new IllegalArgumentException("index must not be negative");
		}
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					fieldName + " must not be blank");
		}
		return value;
	}

	private static BigDecimal requireScore(
			BigDecimal score,
			String fieldName) {
		Objects.requireNonNull(score, fieldName + " must not be null");
		if (score.compareTo(BigDecimal.ZERO) < 0
				|| score.compareTo(new BigDecimal("100")) > 0) {
			throw new IllegalArgumentException(
					fieldName + " must be between 0 and 100");
		}
		return score;
	}
}
