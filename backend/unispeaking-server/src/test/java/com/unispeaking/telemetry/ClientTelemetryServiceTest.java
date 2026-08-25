package com.unispeaking.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientTelemetryServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-19T04:00:00Z");

	@Test
	void bindsAuthenticatedUserAndSanitizesClientFields() {
		List<ClientTelemetryRecord> records = new ArrayList<>();
		ClientTelemetryService service = new ClientTelemetryService(
				records::add,
				Clock.fixed(NOW, ZoneOffset.UTC));
		ClientTelemetryEventRequest event = new ClientTelemetryEventRequest(
				"rtc.quality", "WEB", "INFO", NOW, "install-1", "session-1",
				"https://unispeaking.qnsdk.com/conversation?token=secret#call", "web@abc",
				null, null,
				Map.of("rtt_ms", 123.5, "turn_used", true, "user_id", "forged-user",
						"nested", Map.of("secret", "value")), "evt-stable-1");

		int accepted = service.accept(new ClientTelemetryBatchRequest(List.of(event)), "real-user");

		assertEquals(1, accepted);
		Map<String, Object> fields = records.getFirst().fields();
		assertEquals("real-user", fields.get("user_id"));
		assertEquals("/conversation", fields.get("route"));
		assertEquals(123.5, fields.get("rtt_ms"));
		assertEquals(true, fields.get("turn_used"));
		assertEquals("evt-stable-1", fields.get("event_id"));
		assertFalse(fields.containsKey("nested"));
	}

	@Test
	void usesServerTimeWhenClientClockIsOutsideAllowedWindow() {
		List<ClientTelemetryRecord> records = new ArrayList<>();
		ClientTelemetryService service = new ClientTelemetryService(
				records::add,
				Clock.fixed(NOW, ZoneOffset.UTC));
		ClientTelemetryEventRequest event = new ClientTelemetryEventRequest(
				"app.crash", "MOBILE", "FATAL", NOW.minusSeconds(172_800), "install-1",
				null, null, "mobile@1.0.0", "boom", "stack", Map.of());

		service.accept(new ClientTelemetryBatchRequest(List.of(event)), null);

		assertEquals(NOW.toString(), records.getFirst().fields().get("occurred_at"));
		assertFalse(records.getFirst().fields().containsKey("user_id"));
	}

	@Test
	void continuesAfterSinkFailureAndSanitizesEveryAttributeAndRouteShape() {
		List<ClientTelemetryRecord> records = new ArrayList<>();
		ClientTelemetrySink failing = record -> { throw new IllegalStateException("down"); };
		ClientTelemetryService service = new ClientTelemetryService(
				List.of(failing, records::add), Clock.fixed(NOW, ZoneOffset.UTC));
		Map<String, Object> attributes = new java.util.LinkedHashMap<>();
		attributes.put("integer", 1);
		attributes.put("long_value", 2L);
		attributes.put("finite", 3.5f);
		attributes.put("nan", Double.NaN);
		attributes.put("blank", " ");
		attributes.put("long_text", "x".repeat(600));
		attributes.put("unsupported", List.of("secret"));
		attributes.put("message", "reserved");
		ClientTelemetryEventRequest event = new ClientTelemetryEventRequest(
				"event", "MOBILE", "WARN", NOW.plusSeconds(60), " ", " session ",
				"relative/path?secret=1#fragment", " release ", " message ", " stack ",
				attributes, " ");

		assertEquals(1, service.accept(new ClientTelemetryBatchRequest(List.of(event)), " "));

		Map<String, Object> fields = records.getFirst().fields();
		assertTrue(((String) fields.get("event_id")).length() > 10);
		assertEquals("relative/path", fields.get("route"));
		assertEquals(1, fields.get("integer"));
		assertEquals(2L, fields.get("long_value"));
		assertEquals(3.5d, fields.get("finite"));
		assertEquals(500, ((String) fields.get("long_text")).length());
		assertFalse(fields.containsKey("nan"));
		assertFalse(fields.containsKey("blank"));
		assertFalse(fields.containsKey("unsupported"));
		assertEquals("message", fields.get("message"));
		assertEquals(NOW.plusSeconds(60).toString(), fields.get("occurred_at"));
	}

	@Test
	void handlesNullAttributesBlankRouteAndExtremeClientClock() {
		List<ClientTelemetryRecord> records = new ArrayList<>();
		ClientTelemetryService service = new ClientTelemetryService(
				records::add, Clock.fixed(NOW, ZoneOffset.UTC));
		ClientTelemetryEventRequest event = new ClientTelemetryEventRequest(
				"event", "WEB", "ERROR", Instant.MAX, null, null, " ", null,
				null, null, null, "event-id");

		service.accept(new ClientTelemetryBatchRequest(List.of(event)), "user");

		assertEquals(NOW.toString(), records.getFirst().fields().get("occurred_at"));
		assertFalse(records.getFirst().fields().containsKey("route"));
	}
}
