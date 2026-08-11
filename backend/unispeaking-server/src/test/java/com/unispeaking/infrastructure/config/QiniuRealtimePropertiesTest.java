package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QiniuRealtimePropertiesTest {

	@Test
	void normalizesBaseUrlAndVoiceAliases() {
		QiniuRealtimeProperties properties = new QiniuRealtimeProperties(
				" https://rti.example.test/ ",
				" api-key ",
				" app-id ",
				" model-profile ",
				" role-profile ",
				" scenario ",
				Map.of(" Katerina ", " voice-katerina "),
				Duration.ofSeconds(1),
				Duration.ofSeconds(2),
				1024);

		assertEquals("https://rti.example.test", properties.baseUrl());
		assertEquals("api-key", properties.apiKey());
		assertEquals("voice-katerina", properties.voiceProfile("KATERINA"));
		assertEquals("", properties.voiceProfile("unknown"));
	}

	@Test
	void acceptsAnAbsoluteHttpBaseUrl() {
		QiniuRealtimeProperties properties = validProperties();

		properties.validate();

		assertEquals("https://rti.example.test", properties.baseUri().toString());
	}

	@Test
	void appliesQiniuConnectTimeoutToItsDedicatedHttpClient() {
		QiniuRealtimeProperties properties = validProperties();

		assertEquals(
				properties.connectTimeout(),
				new WebClientConfig()
						.qiniuRealtimeHttpClient(properties)
						.connectTimeout()
						.orElseThrow());
	}

	@Test
	void rejectsNonHttpBaseUrl() {
		QiniuRealtimeProperties properties = new QiniuRealtimeProperties(
				"file:///tmp/rti",
				"key",
				"app",
				"model",
				"role",
				"",
				Map.of(),
				Duration.ofSeconds(1),
				Duration.ofSeconds(1),
				1024);

		assertThrows(IllegalStateException.class, properties::validate);
	}

	@Test
	void rejectsNonPositiveTimeoutAndResponseLimits() {
		QiniuRealtimeProperties properties = new QiniuRealtimeProperties(
				"https://rti.example.test",
				"key",
				"app",
				"model",
				"role",
				"",
				Map.of(),
				Duration.ZERO,
				Duration.ofSeconds(1),
				1024);

		assertThrows(IllegalStateException.class, properties::validate);

		QiniuRealtimeProperties invalidLimit = new QiniuRealtimeProperties(
				"https://rti.example.test",
				"key",
				"app",
				"model",
				"role",
				"",
				Map.of(),
				Duration.ofSeconds(1),
				Duration.ofSeconds(1),
				0);

		assertThrows(IllegalStateException.class, invalidLimit::validate);
	}

	private QiniuRealtimeProperties validProperties() {
		return new QiniuRealtimeProperties(
				"https://rti.example.test",
				"key",
				"app",
				"model",
				"role",
				"",
				Map.of(),
				Duration.ofSeconds(1),
				Duration.ofSeconds(1),
				1024);
	}
}
