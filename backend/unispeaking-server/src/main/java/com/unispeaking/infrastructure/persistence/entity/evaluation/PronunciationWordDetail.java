package com.unispeaking.infrastructure.persistence.entity.evaluation;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pronunciation detail persisted with one custom-scene dialogue turn.
 */
public record PronunciationWordDetail(
		int index,
		String text,
		BigDecimal pronunciationScore,
		List<Phoneme> phonemes) {

	public PronunciationWordDetail {
		phonemes = phonemes == null ? List.of() : List.copyOf(phonemes);
	}

	public record Phoneme(
			int index,
			String expectedPhoneme,
			String actualPhoneme,
			BigDecimal pronunciationScore,
			int startPosition,
			int endPosition) {
	}
}
