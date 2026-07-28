package com.unispeaking.domain.vo.evaluation;

public record FiveDimensionScore(
		Integer pronunciation,
		Integer fluency,
		Integer grammar,
		Integer vocabulary,
		Integer communication) {
}
