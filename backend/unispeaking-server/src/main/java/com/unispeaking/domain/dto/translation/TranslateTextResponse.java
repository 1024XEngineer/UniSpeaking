package com.unispeaking.domain.dto.translation;

public record TranslateTextResponse(
		String sourceText,
		String translatedText,
		String targetLanguage) {
}
