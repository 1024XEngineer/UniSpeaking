package com.unispeaking.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class StructuredLogClientTelemetrySink implements ClientTelemetrySink {

	private static final Logger LOGGER = LoggerFactory.getLogger("com.unispeaking.clienttelemetry");

	private final ObjectMapper objectMapper;

	public StructuredLogClientTelemetrySink(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void write(ClientTelemetryRecord record) {
		try {
			LOGGER.info("{}", objectMapper.writeValueAsString(record.fields()));
		}
		catch (JacksonException exception) {
			LOGGER.warn("client telemetry serialization failed", exception);
		}
	}
}
