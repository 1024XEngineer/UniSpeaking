package com.unispeaking.infrastructure.serialization;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class JsonUtils {

	private JsonUtils() {
	}

	public static String toJson(ObjectMapper objectMapper, Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JacksonException exception) {
			throw new IllegalArgumentException("Failed to serialize JSON", exception);
		}
	}
}
