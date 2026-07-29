package com.unispeaking.service.evaluation.internal.model;

import java.math.BigDecimal;
import java.util.List;

public record PronunciationWordResult(
		int index,
		String word,
		WordReadStatus readStatus,
		BigDecimal overallScore,
		BigDecimal pronunciationScore,
		Boolean isProminent,
		List<PronunciationPhonemeResult> phonemes) {
}
