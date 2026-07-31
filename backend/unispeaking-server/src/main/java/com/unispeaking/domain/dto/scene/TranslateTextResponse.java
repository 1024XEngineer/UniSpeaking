package com.unispeaking.domain.dto.scene;

public record TranslateTextResponse(
		String sourceText,
		String translatedText,
		String targetLanguage) {
}
