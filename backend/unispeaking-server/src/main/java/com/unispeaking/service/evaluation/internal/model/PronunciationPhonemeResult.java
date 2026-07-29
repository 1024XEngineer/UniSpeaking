package com.unispeaking.service.evaluation.internal.model;

import java.math.BigDecimal;

public record PronunciationPhonemeResult(
		int index,
		String expectedPhoneme,
		String actualPhoneme,
		BigDecimal pronunciationScore,
		int startPosition,
		int endPosition) {

	public PronunciationPhonemeResult(
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
}
