package com.unispeaking.telemetry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;

public record ClientTelemetryEventRequest(
		@NotBlank @Pattern(regexp = "[a-z][a-z0-9_.-]{1,63}") String eventType,
		@NotBlank @Pattern(regexp = "WEB|MOBILE") String platform,
		@NotBlank @Pattern(regexp = "INFO|WARN|ERROR|FATAL") String severity,
		@NotNull Instant occurredAt,
		@Size(max = 80) String anonymousId,
		@Size(max = 100) String sessionId,
		@Size(max = 300) String route,
		@Size(max = 100) String release,
		@Size(max = 500) String message,
		@Size(max = 8_000) String stack,
		@Size(max = 32) Map<@Pattern(regexp = "[a-z][a-z0-9_]{0,63}") String, Object> attributes,
		@Size(max = 80) String eventId) {

	public ClientTelemetryEventRequest(
			String eventType,
			String platform,
			String severity,
			Instant occurredAt,
			String anonymousId,
			String sessionId,
			String route,
			String release,
			String message,
			String stack,
			Map<String, Object> attributes) {
		this(eventType, platform, severity, occurredAt, anonymousId, sessionId, route,
				release, message, stack, attributes, null);
	}
}
