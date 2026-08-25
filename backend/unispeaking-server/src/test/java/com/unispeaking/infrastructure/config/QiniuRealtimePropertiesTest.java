package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QiniuRealtimePropertiesTest {

	@Test
	void normalizesAndResolvesAllSupportedValues() {
		Map<String, String> mappings = new LinkedHashMap<>();
		mappings.put(" James ", " qiniu-james ");
		mappings.put("", "ignored");
		mappings.put("Clara", " ");
		QiniuRealtimeProperties properties = properties(" https://api.qiniu.test/// ", "app", "model", "role", "voice", mappings, "platform_rtc", "cn", Duration.ofSeconds(5), 1024);
		properties.validate();
		assertEquals(URI.create("https://api.qiniu.test/session"), properties.controlUri("/session"));
		assertEquals("qiniu-james", properties.resolveVoiceProfile("jAmEs"));
		assertEquals("voice", properties.resolveVoiceProfile(" "));
		assertEquals("custom", properties.resolveVoiceProfile(" custom "));
		assertNull(properties.resolveClientEndpoint(" "));
		assertEquals(URI.create("https://api.qiniu.test/client"), properties.resolveClientEndpoint("/client"));
		assertEquals(URI.create("https://client.qiniu.test/path"), properties.resolveClientEndpoint("https://client.qiniu.test/path"));
		assertThrows(IllegalArgumentException.class, () -> properties.resolveClientEndpoint("http://client.test"));
	}

	@Test
	void rejectsEveryInvalidRequiredSetting() {
		assertInvalid(properties("http://api.test", "app", "model", "role", "voice", null, "platform_rtc", "cn", Duration.ofSeconds(1), 1));
		assertInvalid(properties("", "app", "model", "role", "voice", null, "platform_rtc", "cn", Duration.ofSeconds(1), 1));
		assertInvalid(properties("https://api.test", "", "model", "role", "voice", null, "platform_rtc", "cn", Duration.ofSeconds(1), 1));
		assertInvalid(properties("https://api.test", "app", "", "role", "voice", null, "platform_rtc", "cn", Duration.ofSeconds(1), 1));
		assertInvalid(properties("https://api.test", "app", "model", "", "voice", null, "platform_rtc", "cn", Duration.ofSeconds(1), 1));
		assertInvalid(properties("https://api.test", "app", "model", "role", "", null, "platform_rtc", "cn", Duration.ofSeconds(1), 1));
		assertInvalid(properties("https://api.test", "app", "model", "role", "voice", Map.of(), "wrong", "cn", Duration.ofSeconds(1), 1));
		assertInvalid(properties("https://api.test", "app", "model", "role", "voice", null, "platform_rtc", "", Duration.ofSeconds(1), 1));
		assertInvalid(properties("https://api.test", "app", "model", "role", "voice", null, "platform_rtc", "cn", null, 1));
		assertInvalid(properties("https://api.test", "app", "model", "role", "voice", null, "platform_rtc", "cn", Duration.ZERO, 1));
		assertInvalid(properties("https://api.test", "app", "model", "role", "voice", null, "platform_rtc", "cn", Duration.ofSeconds(-1), 1));
		assertInvalid(properties("https://api.test", "app", "model", "role", "voice", null, "platform_rtc", "cn", Duration.ofSeconds(1), 0));
	}

	private void assertInvalid(QiniuRealtimeProperties properties) {
		assertThrows(RuntimeException.class, properties::validate);
	}

	private QiniuRealtimeProperties properties(String baseUrl, String appId, String model, String role, String voice, Map<String, String> mappings, String transport, String region, Duration timeout, int maxBytes) {
		return new QiniuRealtimeProperties(baseUrl, " api-key ", appId, model, role, voice, mappings, transport, region, timeout, maxBytes);
	}
}
