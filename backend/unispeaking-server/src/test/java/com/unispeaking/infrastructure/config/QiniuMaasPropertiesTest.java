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
				Duration.ofSeconds(30),
				2_097_152,
				4096);

		assertThrows(IllegalStateException.class, properties::validate);
	}

	@Test
	void defaultsMissingTimeoutsToBoundedValues() {
		QiniuMaasProperties properties = new QiniuMaasProperties(
				"https://api.qnaigc.com/v1",
				"key",
				"deepseek/deepseek-v4-flash",
				"qwen/qwen3.5-plus",
				null,
				null,
				2_097_152,
				4096);

		assertEquals(Duration.ofSeconds(10), properties.connectTimeout());
		assertEquals(Duration.ofSeconds(30), properties.readTimeout());
	}

	@Test
	void normalizesBlankAndNonPositiveConstructorValues() {
		QiniuMaasProperties properties = new QiniuMaasProperties(
				"  ", null, " ", null,
				Duration.ZERO, Duration.ofSeconds(-1), 0, -1);

		assertEquals("https://api.qnaigc.com/v1", properties.baseUrl());
		assertEquals("", properties.apiKey());
		assertEquals("deepseek/deepseek-v4-flash", properties.primaryModel());
		assertEquals("qwen/qwen3.5-plus", properties.fallbackModel());
		assertEquals(Duration.ofSeconds(10), properties.connectTimeout());
		assertEquals(Duration.ofSeconds(30), properties.readTimeout());
		assertEquals(2 * 1024 * 1024, properties.maxResponseBytes());
		assertEquals(4096, properties.maxOutputTokens());
		properties.validate();
	}

	@Test
	void trimsValuesAndEveryTrailingSlash() {
		QiniuMaasProperties properties = new QiniuMaasProperties(
				" https://api.qnaigc.com/v1/// ", " key ", " primary ", " fallback ",
				Duration.ofSeconds(1), Duration.ofSeconds(2), 1, 2);

		assertEquals("https://api.qnaigc.com/v1", properties.baseUrl());
		assertEquals("key", properties.apiKey());
		assertEquals("primary", properties.primaryModel());
		assertEquals("fallback", properties.fallbackModel());
		assertEquals(Duration.ofSeconds(1), properties.connectTimeout());
		assertEquals(1, properties.maxResponseBytes());
	}

	@Test
	void rejectsEveryUnsafeEndpointShapeAndMalformedUri() {
		String[] invalid = {
				"not a uri",
				"/v1",
				"http://api.qnaigc.com/v1",
				"https://evil.example/v1",
				"https://user@api.qnaigc.com/v1",
				"https://api.qnaigc.com:443/v1",
				"https://api.qnaigc.com/v2",
				"https://api.qnaigc.com/v1?q=1",
				"https://api.qnaigc.com/v1#fragment"
		};

		for (String baseUrl : invalid) {
			assertThrows(IllegalStateException.class, () -> properties(baseUrl).validate(), baseUrl);
		}
	}

	private QiniuMaasProperties properties(String baseUrl) {
		return new QiniuMaasProperties(
				baseUrl,
				"key",
				"deepseek/deepseek-v4-flash",
				"qwen/qwen3.5-plus",
				Duration.ofSeconds(10),
				Duration.ofSeconds(30),
				2_097_152,
				4096);
	}
}
