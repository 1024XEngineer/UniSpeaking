package com.unispeaking.service.translation.impl;

import com.unispeaking.domain.dto.translation.TranslateTextResponse;
import com.unispeaking.exception.BusinessException;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.translation.TranslationService;
import org.springframework.stereotype.Service;

@Service
public class TranslationServiceImpl implements TranslationService {

	private final AuthService authService;
	private final AiProviderRegistry providerRegistry;

	public TranslationServiceImpl(
			AuthService authService,
			AiProviderRegistry providerRegistry) {
		this.authService = authService;
		this.providerRegistry = providerRegistry;
	}

	@Override
	public TranslateTextResponse translateToSimplifiedChinese(String text) {
		authService.requireUserId(null);
		String sourceText = requireText(text);
		String prompt = """
				Translate the text enclosed in <source> into natural Simplified Chinese.
				Preserve the original meaning, tone, names, numbers, and punctuation.
				Return only the translation. Do not explain, annotate, or quote the source.

				<source>
				%s
				</source>
				""".formatted(sourceText);
		String translatedText = providerRegistry.executeLlmTask(
				AiProviderRegistry.QWEN_LLM_PLUS,
				prompt,
				null);
		if (translatedText == null || translatedText.isBlank()) {
			throw new BusinessException("TRANSLATION_EMPTY", "翻译模型没有返回有效文本");
		}
		return new TranslateTextResponse(sourceText, translatedText.strip(), "zh-CN");
	}

	private String requireText(String text) {
		if (text == null || text.isBlank()) {
			throw new BusinessException("TRANSLATION_TEXT_REQUIRED", "待翻译文本不能为空");
		}
		String normalized = text.strip();
		if (normalized.length() > 4000) {
			throw new BusinessException("TRANSLATION_TEXT_TOO_LONG", "待翻译文本不能超过4000个字符");
		}
		return normalized;
	}
}
