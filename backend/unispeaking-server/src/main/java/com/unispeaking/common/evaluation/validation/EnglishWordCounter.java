package com.unispeaking.common.evaluation.validation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统计 transcript 中有效英文词，并给出是否达到评分长度要求的分类。
 *
 * <p>计数结果同时用于单轮回答的过短判断和整场报告的单轮权重，
 * 两处必须复用同一套词法规则，避免评分资格与汇总权重不一致。</p>
 */
public final class EnglishWordCounter {

	private static final int MIN_SCORABLE_WORD_COUNT = 4;

	/*
	 * 仅统计 ASCII 英文字母；直撇号、弯撇号和连字符只有夹在字母段之间时才属于同一词。
	 * 两侧使用 Unicode 字母边界，避免把 café 或“中文hello中文”中的局部 ASCII 字母误计为英文词。
	 */
	private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile(
			"(?<!\\p{L})[A-Za-z]+(?:['’-][A-Za-z]+)*(?!\\p{L})");

	private EnglishWordCounter() {
	}

	/**
	 * 分析英文词数：零词为空内容，1 至 3 词为过短，4 词及以上可正常评分。
	 *
	 * @param transcript 待分析的转写文本，允许为 {@code null}
	 * @return 有效英文词数及对应的评分长度分类
	 */
	public static Analysis analyze(String transcript) {
		if (transcript == null || transcript.isBlank()) {
			return new Analysis(0, Classification.EMPTY);
		}

		Matcher matcher = ENGLISH_WORD_PATTERN.matcher(transcript);
		int validWordCount = 0;
		while (matcher.find()) {
			validWordCount++;
		}

		Classification classification;
		if (validWordCount == 0) {
			classification = Classification.EMPTY;
		} else if (validWordCount < MIN_SCORABLE_WORD_COUNT) {
			classification = Classification.TOO_SHORT;
		} else {
			classification = Classification.SCORABLE;
		}
		return new Analysis(validWordCount, classification);
	}

	/**
	 * 英文词计数及其业务分类的一次不可变分析结果。
	 */
	public record Analysis(
			int validWordCount,
			Classification classification) {
	}

	/**
	 * transcript 是否包含内容，以及是否达到四个有效英文词的评分门槛。
	 */
	public enum Classification {
		EMPTY,
		TOO_SHORT,
		SCORABLE
	}
}
