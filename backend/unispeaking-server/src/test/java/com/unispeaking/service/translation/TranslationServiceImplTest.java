package com.unispeaking.service.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.auth.AuthService;
import com.unispeaking.service.translation.impl.TranslationServiceImpl;
import org.junit.jupiter.api.Test;

class TranslationServiceImplTest {

	@Test
	void translatesAuthenticatedUsersTextToSimplifiedChinese() {
		AuthService authService = mock(AuthService.class);
		AiProviderRegistry providerRegistry = mock(AiProviderRegistry.class);
		when(authService.requireUserId(null)).thenReturn("user-1");
		when(providerRegistry.executeLlmTask(
				eq(AiProviderRegistry.QWEN_LLM_PLUS),
				any(),
				eq(null)))
				.thenReturn("你好，很高兴认识你。");
		TranslationService service = new TranslationServiceImpl(
				authService,
				providerRegistry);

		var response = service.translateToSimplifiedChinese("Hello, nice to meet you.");

		assertEquals("Hello, nice to meet you.", response.sourceText());
		assertEquals("你好，很高兴认识你。", response.translatedText());
		assertEquals("zh-CN", response.targetLanguage());
	}
}
