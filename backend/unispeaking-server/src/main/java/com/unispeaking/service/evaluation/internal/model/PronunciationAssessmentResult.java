package com.unispeaking.service.evaluation.internal.model;

import java.math.BigDecimal;
import java.util.List;

public record PronunciationAssessmentResult(
		BigDecimal overallScore,
		BigDecimal rhythmScore,
		BigDecimal toneScore,
		BigDecimal integrityScore,
		BigDecimal pronunciationScore,
		BigDecimal fluencyScore,
		EndingTone endingTone,
		List<PronunciationWordResult> words) {
}
