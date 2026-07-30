package com.unispeaking.infrastructure.persistence.evaluation.utterance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 评分模块内部使用的单轮气泡持久化模型。
 *
 * <p>模型不暴露数据库记录 ID；同一会话和气泡序号共同标识一条业务记录。
 * words 允许为空，用于保存“过短，不予评分”的气泡。</p>
 *
 * @param sessionId 练习会话标识
 * @param utteranceNo 用户气泡序号，从 1 开始
 * @param transcript 用户回答的原始转写
 * @param aiText 当前回答对应的上一条 AI 发言，允许为空
 * @param overallScore 单轮综合分
 * @param rhythmScore 节奏分
 * @param toneScore 语调分
 * @param integrityScore 完整度分
 * @param pronunciationScore 发音准确度分
 * @param fluencyScore 流利度分
 * @param feedbackSummary 评价摘要
 * @param suggestedExpression 建议表达，过短记录允许为空字符串
 * @param words 按朗读顺序保存的单词与音素明细
 */
public record PracticeResultUtterance(
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
		List<Word> words) {

	private static final BigDecimal MAX_SCORE = new BigDecimal("100");

	/**
	 * 校验数据库必要字段，并保存单词明细的不可变快照。
	 */
	public PracticeResultUtterance {
		sessionId = Objects.requireNonNull(
				sessionId,
				"sessionId must not be null");
		if (utteranceNo < 1) {
			throw new IllegalArgumentException(
					"utteranceNo must be at least 1");
		}
		transcript = requireText(transcript, "transcript");
		aiText = normalizeOptionalText(aiText);
		overallScore = requireScore(overallScore, "overallScore");
		rhythmScore = requireScore(rhythmScore, "rhythmScore");
		toneScore = optionalScore(toneScore, "toneScore");
		integrityScore = requireScore(integrityScore, "integrityScore");
		pronunciationScore = requireScore(
				pronunciationScore,
				"pronunciationScore");
		fluencyScore = requireScore(fluencyScore, "fluencyScore");
		feedbackSummary =
				requireText(feedbackSummary, "feedbackSummary").trim();
		suggestedExpression = Objects.requireNonNull(
				suggestedExpression,
				"suggestedExpression must not be null").trim();
		words = List.copyOf(Objects.requireNonNull(
				words,
				"words must not be null"));
	}

	/**
	 * 单轮发音明细中的单词。
	 *
	 * @param index 单词在供应商结果中的零基序号
	 * @param text 单词文本
	 * @param pronunciationScore 单词发音分
	 * @param phonemes 按发音顺序保存的音素
	 */
	public record Word(
			int index,
			String text,
			BigDecimal pronunciationScore,
			List<Phoneme> phonemes) {

		/**
		 * 正常评分单词必须包含至少一个完整音素。
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
	 * 单轮发音明细中的音素。
	 *
	 * @param index 音素在单词中的零基序号
	 * @param expectedPhoneme 标准音素
	 * @param actualPhoneme 用户实际音素
	 * @param pronunciationScore 音素发音分
	 * @param startPosition 音素开始位置，单位 10 ms
	 * @param endPosition 音素结束位置，单位 10 ms
	 */
	public record Phoneme(
			int index,
			String expectedPhoneme,
			String actualPhoneme,
			BigDecimal pronunciationScore,
			int startPosition,
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
		 * 标准音素、实际音素和数值评分必须完整存在。
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

	private static String normalizeOptionalText(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private static BigDecimal requireScore(
			BigDecimal value,
			String fieldName) {
		Objects.requireNonNull(value, fieldName + " must not be null");
		return validateScore(value, fieldName);
	}

	private static BigDecimal optionalScore(
			BigDecimal value,
			String fieldName) {
		return value == null ? null : validateScore(value, fieldName);
	}

	private static BigDecimal validateScore(
			BigDecimal value,
			String fieldName) {
		if (value.compareTo(BigDecimal.ZERO) < 0
				|| value.compareTo(MAX_SCORE) > 0) {
			throw new IllegalArgumentException(
					fieldName + " must be between 0 and 100");
		}
		return value;
	}
}
