package com.unispeaking.domain.dto.asset;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LearningAssetSummary(
		String sceneId,
		String title,
		String background,
		int wordCount,
		int phraseCount,
		int sentenceCount,
		String latestSessionId,
		BigDecimal latestScore,
		OffsetDateTime latestPracticedAt,
		int practiceCount,
		OffsetDateTime createdAt) {
}
