package com.unispeaking.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
