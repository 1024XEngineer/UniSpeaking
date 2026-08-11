package com.unispeaking.admin.usage.domain;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ModelUsage(
        long responseCount,
        long totalTokens,
        long inputTokens,
        long outputTokens,
        long inputTextTokens,
        long inputAudioTokens,
        long outputTextTokens,
        long outputAudioTokens) {
}
