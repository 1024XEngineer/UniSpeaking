package com.unispeaking.admin.quality;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.context.SecurityContextHolder;
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

	@Test
	void ignoresNonActionableAndUnauthenticatedOutcomesWithoutDatabaseWrites() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		QualityIssueTelemetrySink sink = new QualityIssueTelemetrySink(jdbc, new ObjectMapper());

		sink.write(new ClientTelemetryRecord(Map.of("level", "info", "outcome", "success")));
		sink.write(new ClientTelemetryRecord(Map.of("level", "fatal", "outcome", "unauthenticated")));

		verify(jdbc, never()).queryForObject(anyString(), eq(UUID.class), any(Object[].class));
		verify(jdbc, never()).update(anyString(), any(Object[].class));
	}

	@Test
	void normalizesActionableFieldsAndDoesNotIncrementForDuplicateEvent() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		UUID issueId = UUID.randomUUID();
		when(jdbc.queryForObject(anyString(), eq(UUID.class), any(Object[].class))).thenReturn(issueId);
		when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
		QualityIssueTelemetrySink sink = new QualityIssueTelemetrySink(jdbc, new ObjectMapper());
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("event_id", "event-1");
		fields.put("platform", "desktop");
		fields.put("level", "fatal");
		fields.put("event_type", " ");
		fields.put("http_status", "not-a-number");
		fields.put("outcome", "network_error");
		fields.put("message", "  Offline  ");

		sink.write(new ClientTelemetryRecord(fields));

		ArgumentCaptor<Object[]> issueArguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).queryForObject(anyString(), eq(UUID.class), issueArguments.capture());
		assertEquals("BACKEND", issueArguments.getValue()[1]);
		assertEquals("CRITICAL", issueArguments.getValue()[2]);
		assertEquals("Offline", issueArguments.getValue()[3]);
		assertEquals("Offline", issueArguments.getValue()[4]);
		ArgumentCaptor<Object[]> eventArguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).update(anyString(), eventArguments.capture());
		assertEquals("BACKEND", eventArguments.getValue()[5]);
		assertEquals("client.error", eventArguments.getValue()[6]);
		assertEquals("FATAL", eventArguments.getValue()[7]);
	}

	@Test
	void captureBackendProducesAnActionableBackendRecordWithoutAuthentication() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		UUID issueId = UUID.randomUUID();
		when(jdbc.queryForObject(anyString(), eq(UUID.class), any(Object[].class))).thenReturn(issueId);
		when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
		QualityIssueTelemetrySink sink = new QualityIssueTelemetrySink(jdbc, new ObjectMapper());
		SecurityContextHolder.clearContext();

		try {
			sink.captureBackend(new IllegalStateException("broken"), "/api/practice", "POST", 503, "UPSTREAM");
		}
		finally {
			SecurityContextHolder.clearContext();
		}

		ArgumentCaptor<Object[]> issueArguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).queryForObject(anyString(), eq(UUID.class), issueArguments.capture());
		assertEquals("BACKEND", issueArguments.getValue()[1]);
		assertEquals("HIGH", issueArguments.getValue()[2]);
		assertEquals("503 · /api/practice", issueArguments.getValue()[3]);
		assertEquals("broken", issueArguments.getValue()[4]);
		verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), any(Object[].class));
	}

	@Test
	void captureBackendIncludesAValidAuthenticatedUserIdAndNormalizesInvalidOptionalValues() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		UUID issueId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(jdbc.queryForObject(anyString(), eq(UUID.class), any(Object[].class))).thenReturn(issueId);
		when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
		QualityIssueTelemetrySink sink = new QualityIssueTelemetrySink(jdbc, new ObjectMapper());
		Jwt jwt = Jwt.withTokenValue("token").subject(userId.toString()).header("alg", "none").build();
		var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(jwt, null);
		SecurityContextHolder.getContext().setAuthentication(authentication);

		try {
			sink.captureBackend(new RuntimeException((String) null), " /api/error ", "GET", 500, null);
		}
		finally {
			SecurityContextHolder.clearContext();
		}

		ArgumentCaptor<Object[]> eventArguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), eventArguments.capture());
		assertEquals(userId, eventArguments.getAllValues().getFirst()[2]);
		assertEquals("/api/error", eventArguments.getAllValues().getFirst()[9]);
		assertEquals("/api/error", eventArguments.getAllValues().getFirst()[12]);
		assertEquals("GET", eventArguments.getAllValues().getFirst()[13]);
		assertEquals(500, eventArguments.getAllValues().getFirst()[14]);
	}

	@Test
	void handlesMalformedDatesIdsNumbersAndTruncatesLongTitles() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		UUID issueId = UUID.randomUUID();
		when(jdbc.queryForObject(anyString(), eq(UUID.class), any(Object[].class))).thenReturn(issueId);
		when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
		QualityIssueTelemetrySink sink = new QualityIssueTelemetrySink(jdbc, new ObjectMapper());
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("event_id", "event-malformed");
		fields.put("platform", "cross-platform");
		fields.put("event_type", "client.crash");
		fields.put("level", "unexpected-level");
		fields.put("outcome", "error");
		fields.put("occurred_at", "not-an-instant");
		fields.put("user_id", "not-a-uuid");
		fields.put("http_status", "not-a-number");
		fields.put("duration_ms", "not-a-number");
		fields.put("message", "x".repeat(300));

		sink.write(new ClientTelemetryRecord(fields));

		ArgumentCaptor<Object[]> issueArguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).queryForObject(anyString(), eq(UUID.class), issueArguments.capture());
		assertEquals("BACKEND", issueArguments.getValue()[1]);
		assertEquals("MEDIUM", issueArguments.getValue()[2]);
		assertEquals(200, ((String) issueArguments.getValue()[3]).length());
		assertEquals(300, ((String) issueArguments.getValue()[4]).length());
		ArgumentCaptor<Object[]> eventArguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), eventArguments.capture());
		assertEquals(null, eventArguments.getAllValues().getFirst()[2]);
		assertEquals("ERROR", eventArguments.getAllValues().getFirst()[7]);
		assertEquals(null, eventArguments.getAllValues().getFirst()[14]);
		assertEquals(null, eventArguments.getAllValues().getFirst()[22]);
	}

	@Test
	void captureBackendIgnoresInvalidJwtSubjectWithoutFailingTelemetry() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		UUID issueId = UUID.randomUUID();
		when(jdbc.queryForObject(anyString(), eq(UUID.class), any(Object[].class))).thenReturn(issueId);
		when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
		QualityIssueTelemetrySink sink = new QualityIssueTelemetrySink(jdbc, new ObjectMapper());
		Jwt jwt = Jwt.withTokenValue("token").subject("not-a-uuid").header("alg", "none").build();
		SecurityContextHolder.getContext().setAuthentication(
				new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(jwt, null));

		try {
			assertDoesNotThrow(() -> sink.captureBackend(new IllegalArgumentException("bad"), "/api", "GET", 500, "E"));
		}
		finally {
			SecurityContextHolder.clearContext();
		}

		ArgumentCaptor<Object[]> eventArguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), eventArguments.capture());
		assertEquals(null, eventArguments.getAllValues().getFirst()[2]);
	}
}
