package com.unispeaking.infrastructure.persistence.entity.evaluation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * {@code asset_sentences.reading_details} 的完整 JSONB 持久化投影。
 *
 * <p>句级 toneScore 不属于数据库约定，因此本结构只保存 ending_tone，不增加
 * tone_score。单词和音素明细保留供应商解析后的完整语义。</p>
 */
@JsonPropertyOrder({
	"overall_score",
	"pronunciation_score",
	"fluency_score",
	"integrity_score",
	"rhythm_score",
	"ending_tone",
	"words"
})
public record ReadingDetailsJson(
		@JsonProperty("overall_score")
		BigDecimal overallScore,
		@JsonProperty("pronunciation_score")
		BigDecimal pronunciationScore,
		@JsonProperty("fluency_score")
		BigDecimal fluencyScore,
		@JsonProperty("integrity_score")
		BigDecimal integrityScore,
		@JsonProperty("rhythm_score")
		BigDecimal rhythmScore,
		@JsonProperty("ending_tone")
		EndingTone endingTone,
		@JsonProperty("words")
		List<Word> words) {

	/**
	 * 跟读评分必须包含所有句级分数及至少一个单词明细。
	 */
	public ReadingDetailsJson {
		overallScore = requireScore(overallScore, "overallScore");
		pronunciationScore =
				requireScore(pronunciationScore, "pronunciationScore");
		fluencyScore = requireScore(fluencyScore, "fluencyScore");
		integrityScore = requireScore(integrityScore, "integrityScore");
		rhythmScore = requireScore(rhythmScore, "rhythmScore");
		endingTone = Objects.requireNonNull(
				endingTone,
				"endingTone must not be null");
		words = List.copyOf(Objects.requireNonNull(
				words,
				"words must not be null"));
		if (words.isEmpty()) {
			throw new IllegalArgumentException(
					"reading words must not be empty");
		}
	}

	/**
	 * 跟读 JSONB 中的单词评分。
	 */
	@JsonPropertyOrder({
		"index",
		"text",
		"read_status",
		"overall_score",
		"pronunciation_score",
		"is_prominent",
		"phonemes"
	})
	public record Word(
			@JsonProperty("index")
			int index,
			@JsonProperty("text")
			String text,
			@JsonProperty("read_status")
			WordReadStatus readStatus,
			@JsonProperty("overall_score")
			BigDecimal overallScore,
			@JsonProperty("pronunciation_score")
			BigDecimal pronunciationScore,
			@JsonProperty("is_prominent")
			@JsonInclude(JsonInclude.Include.ALWAYS)
			Boolean isProminent,
			@JsonProperty("phonemes")
			List<Phoneme> phonemes) {

		/**
		 * 保存不可变音素列表，并拒绝缺失或结构不完整的正常评分单词。
		 */
		public Word {
			requireIndex(index);
			text = requireText(text, "text");
			readStatus = Objects.requireNonNull(
					readStatus,
					"readStatus must not be null");
			overallScore = requireScore(overallScore, "overallScore");
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
	 * 跟读 JSONB 中的音素评分，同时保存标准音素和用户实际音素。
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
		 * 音素评分必须能够完整还原公开响应，不允许使用 symbol 合并两种音素。
		 */
		public Phoneme {
			requireIndex(index);
			expectedPhoneme =
					requireText(expectedPhoneme, "expectedPhoneme");
			actualPhoneme = requireText(actualPhoneme, "actualPhoneme");
			pronunciationScore =
					requireScore(pronunciationScore, "pronunciationScore");
			boolean unmatched = startPosition == -1 && endPosition == -1;
			if (!unmatched
					&& (startPosition < 0
							|| endPosition <= startPosition)) {
				throw new IllegalArgumentException(
						"phoneme positions must define a positive duration or be unmatched");
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
