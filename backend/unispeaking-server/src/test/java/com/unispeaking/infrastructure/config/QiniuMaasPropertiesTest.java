package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class QiniuMaasPropertiesTest {

	@Test
	void acceptsBothDocumentedQiniuMaasEndpoints() {
		QiniuMaasProperties current = properties("https://api.qnaigc.com/v1");
		QiniuMaasProperties legacy = properties("https://openai.sufy.com/v1/");

		current.validate();
		legacy.validate();
		assertEquals(
				"https://openai.sufy.com/v1/chat/completions",
				legacy.chatCompletionsUri().toString());
	}

	@Test
	void rejectsUntrustedEndpointsBeforeCredentialsCanBeSent() {
		QiniuMaasProperties properties = properties("https://evil.example/v1");

		assertThrows(IllegalStateException.class, properties::validate);
	}

	@Test
	void rejectsDuplicatePrimaryAndFallbackModels() {
		QiniuMaasProperties properties = new QiniuMaasProperties(
				"https://api.qnaigc.com/v1",
				"key",
				"same-model",
				"same-model",
				Duration.ofSeconds(10),
				Duration.ofSeconds(90),
				2_097_152,
				4096);

		assertThrows(IllegalStateException.class, properties::validate);
	}

	private QiniuMaasProperties properties(String baseUrl) {
		return new QiniuMaasProperties(
				baseUrl,
				"key",
				"deepseek/deepseek-v4-flash",
				"qwen/qwen3.5-plus",
				Duration.ofSeconds(10),
				Duration.ofSeconds(90),
				2_097_152,
				4096);
	}
}
