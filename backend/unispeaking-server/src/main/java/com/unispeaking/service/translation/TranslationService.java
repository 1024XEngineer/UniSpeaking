package com.unispeaking.service.translation;

import com.unispeaking.domain.dto.translation.TranslateTextResponse;

public interface TranslationService {

	TranslateTextResponse translateToSimplifiedChinese(String text);
}
