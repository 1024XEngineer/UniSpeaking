package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FreeChatRedisPropertiesTest {

	@Test
	void appliesSafeDefaults() {
		var properties = new FreeChatRedisProperties(null, null);

		assertEquals("unispeaking:free-chat:session", properties.keyPrefix());
		assertEquals(Duration.ofHours(24), properties.ttl());
	}

	@Test
	void rejectsNonPositiveTtl() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new FreeChatRedisProperties("free-chat", Duration.ZERO));
	}
}
