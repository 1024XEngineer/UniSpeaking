package com.unispeaking.admin.quality;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.telemetry.ClientTelemetryRecord;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class QualityIssueTelemetrySinkTest {

	@Test
	void ignoresExpectedUnauthenticatedRequests() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		QualityIssueTelemetrySink sink = new QualityIssueTelemetrySink(jdbc, new ObjectMapper());

		sink.write(new ClientTelemetryRecord(Map.of(
				"event_id", "evt-401",
				"event_type", "api.request",
				"platform", "web",
				"level", "error",
				"http_status", 401,
				"outcome", "error")));

		verify(jdbc, never()).queryForObject(anyString(), eq(UUID.class), any(Object[].class));
		verify(jdbc, never()).update(anyString(), any(Object[].class));
	}

	@Test
	void aggregatesActionableErrorsAndDeduplicatesTheirEvents() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		UUID issueId = UUID.randomUUID();
		when(jdbc.queryForObject(anyString(), eq(UUID.class), any(Object[].class))).thenReturn(issueId);
		when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
		QualityIssueTelemetrySink sink = new QualityIssueTelemetrySink(jdbc, new ObjectMapper());

		assertDoesNotThrow(() -> sink.write(new ClientTelemetryRecord(Map.of(
				"event_id", "evt-timeout-1",
				"event_type", "api.request",
				"platform", "mobile",
				"level", "error",
				"occurred_at", Instant.parse("2026-08-20T02:00:00Z").toString(),
				"api_path", "/api/sessions",
				"api_method", "POST",
				"outcome", "timeout",
				"message", "请求超时"))));

		verify(jdbc).queryForObject(anyString(), eq(UUID.class), any(Object[].class));
		verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), any(Object[].class));
	}
}
