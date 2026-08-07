package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WebOriginPropertiesTest {

	@Test
	void includesLocalAndProductionOriginsByDefault() {
		var properties = new WebOriginProperties();

		assertTrue(properties.getAllowedOriginPatterns().contains("http://localhost:*"));
		assertTrue(properties.getAllowedOriginPatterns().contains("https://unispeaking.cn"));
	}

	@Test
	void acceptsAnExplicitOriginList() {
		var properties = new WebOriginProperties();

		properties.setAllowedOriginPatterns(List.of("https://example.com"));

		assertEquals(List.of("https://example.com"), properties.getAllowedOriginPatterns());
	}
}
