package com.unispeaking.telemetry;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClientTelemetryService {
	private static final Logger LOGGER = LoggerFactory.getLogger(ClientTelemetryService.class);

	private static final Set<String> RESERVED_ATTRIBUTE_KEYS = Set.of(
			"telemetry", "service_name", "event_type", "platform", "level", "user_id",
			"anonymous_id", "session_id", "route", "release", "message", "stack", "occurred_at");
	private static final int MAX_ATTRIBUTE_STRING_LENGTH = 500;
	private static final Duration MAX_CLOCK_SKEW = Duration.ofDays(1);

	private final List<ClientTelemetrySink> sinks;
	private final Clock clock;

	@Autowired
	public ClientTelemetryService(List<ClientTelemetrySink> sinks) {
		this(sinks, Clock.systemUTC());
	}

	ClientTelemetryService(ClientTelemetrySink sink, Clock clock) {
		this(List.of(sink), clock);
	}

	ClientTelemetryService(List<ClientTelemetrySink> sinks, Clock clock) {
		this.sinks = List.copyOf(sinks);
		this.clock = clock;
	}

	public int accept(ClientTelemetryBatchRequest batch, String authenticatedUserId) {
		String userId = trimToNull(authenticatedUserId, 80);
		for (ClientTelemetryEventRequest event : batch.events()) {
			var record = new ClientTelemetryRecord(toFields(event, userId));
			for (ClientTelemetrySink sink : sinks) {
				try {
					sink.write(record);
				}
				catch (RuntimeException exception) {
					LOGGER.warn("client telemetry sink failed: {}", sink.getClass().getSimpleName(), exception);
				}
			}
		}
		return batch.events().size();
	}

	private Map<String, Object> toFields(ClientTelemetryEventRequest event, String userId) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("telemetry", true);
		fields.put("event_id", trimToNull(event.eventId(), 80) == null
				? UUID.randomUUID().toString()
				: trimToNull(event.eventId(), 80));
		fields.put("service_name", "client-telemetry");
		fields.put("event_type", event.eventType());
		fields.put("platform", event.platform().toLowerCase());
		fields.put("level", event.severity().toLowerCase());
		putIfPresent(fields, "user_id", userId);
		putIfPresent(fields, "anonymous_id", trimToNull(event.anonymousId(), 80));
		putIfPresent(fields, "session_id", trimToNull(event.sessionId(), 100));
		putIfPresent(fields, "route", sanitizeRoute(event.route()));
		putIfPresent(fields, "release", trimToNull(event.release(), 100));
		putIfPresent(fields, "message", trimToNull(event.message(), 500));
		putIfPresent(fields, "stack", trimToNull(event.stack(), 8_000));
		fields.put("occurred_at", normalizeOccurredAt(event.occurredAt()).toString());

		if (event.attributes() != null) {
			event.attributes().forEach((key, value) -> {
				if (!RESERVED_ATTRIBUTE_KEYS.contains(key)) {
					Object sanitized = sanitizeAttribute(value);
					if (sanitized != null) fields.put(key, sanitized);
				}
			});
		}
		return fields;
	}

	private Instant normalizeOccurredAt(Instant occurredAt) {
		Instant now = clock.instant();
		try {
			if (Duration.between(now, occurredAt).abs().compareTo(MAX_CLOCK_SKEW) <= 0) {
				return occurredAt;
			}
		}
		catch (ArithmeticException ignored) {
			// Fall through to the server timestamp for invalid client clocks.
		}
		return now;
	}

	private Object sanitizeAttribute(Object value) {
		if (value instanceof Boolean || value instanceof Integer || value instanceof Long) return value;
		if (value instanceof Number number) {
			double numeric = number.doubleValue();
			return Double.isFinite(numeric) ? numeric : null;
		}
		if (value instanceof String text) return trimToNull(text, MAX_ATTRIBUTE_STRING_LENGTH);
		return null;
	}

	private String sanitizeRoute(String route) {
		String value = trimToNull(route, 300);
		if (value == null) return null;
		try {
			URI uri = URI.create(value);
			String path = uri.getPath();
			return trimToNull(path == null ? value.split("[?#]", 2)[0] : path, 300);
		}
		catch (IllegalArgumentException exception) {
			return trimToNull(value.split("[?#]", 2)[0], 300);
		}
	}

	private void putIfPresent(Map<String, Object> fields, String key, Object value) {
		if (value != null) fields.put(key, value);
	}

	private String trimToNull(String value, int maxLength) {
		if (!StringUtils.hasText(value)) return null;
		String trimmed = value.trim();
		return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
	}
}
