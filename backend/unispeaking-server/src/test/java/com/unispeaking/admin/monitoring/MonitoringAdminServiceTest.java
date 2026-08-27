package com.unispeaking.admin.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MonitoringAdminServiceTest {
	private static final Instant EVENT_TIME = Instant.parse("2026-08-25T00:00:00Z");

	@Test
	void returnsHonestEmptyDataWhenPrometheusIsUnavailable() throws Exception {
		MonitoringAdminService.MonitoringResponse response = new MonitoringAdminService(
				jdbcWithRows(false), "http://127.0.0.1:1").overview("invalid-range");
		assertEquals("UP", response.summary().backendStatus());
		assertEquals(0, response.summary().apiErrorRate5m());
		assertEquals(0, response.summary().api5xxCount24h());
		assertEquals(0, response.summary().apiP95Milliseconds24h());
		assertEquals(3, response.platformSummaries().size());
		assertEquals(5, response.performanceEndpoints().size());
		assertEquals(14_500, response.performanceEndpoints().getFirst().previousPeriodP95Milliseconds());
		assertEquals(null, response.performanceEndpoints().getFirst().currentPeriodP95Milliseconds());
	}

	@Test
	void mapsProductionMetricsAndUsesRequestedTrendRange() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		List<String> requests = new CopyOnWriteArrayList<>();
		server.createContext("/api/v1/query", exchange -> {
			String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(),
					java.nio.charset.StandardCharsets.UTF_8);
			if (query.contains("http_request_method")) {
				String value = "7.25";
				respond(exchange, requests, "{\"data\":{\"result\":[{\"metric\":{" +
						"\"http_route\":\"/api/custom-scenes/generate\",\"http_request_method\":\"POST\"}," +
						"\"value\":[\"0\",\"" + value + "\"]}]}}" );
			}
			else {
				respond(exchange, requests,
						"{\"data\":{\"result\":[{\"value\":[\"0\",\"2.5\"]}]}}");
			}
		});
		server.createContext("/api/v1/query_range", exchange -> respond(exchange, requests,
				"{\"data\":{\"result\":[{\"values\":[[\"0\",\"1.0\"],[\"900\",\"2.0\"]]}]}}"));
		server.start();
		try {
			MonitoringAdminService.MonitoringResponse response = new MonitoringAdminService(
					jdbcWithRows(true), "http://127.0.0.1:" + server.getAddress().getPort()).overview("6h");
			assertEquals(2.5, response.summary().apiErrorRate5m());
			assertEquals(3, response.summary().api5xxCount24h());
			assertEquals(2500, response.summary().apiP95Milliseconds24h());
			assertEquals(11, response.governance().pendingIssues());
			assertEquals(14, response.governance().resolvedBugs7d());
			assertEquals(3, response.summary().affectedUsers24h());
			assertEquals("TIMEOUT", response.problems().getFirst().problem());
			assertEquals("request-1", response.recentEvents().getFirst().requestId());
			assertEquals(50, response.performanceEndpoints().getFirst().improvementRate());
			assertEquals("OPTIMIZED", response.performanceEndpoints().getFirst().status());
			assertEquals(2, response.trend().stream().filter(point -> point.clientErrors() != null).count());
			assertTrue(requests.stream().anyMatch(query -> query.contains("step=900")));
		}
		finally {
			server.stop(0);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private JdbcTemplate jdbcWithRows(boolean populated) throws Exception {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.queryForObject(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
			RowMapper mapper = invocation.getArgument(1);
			ResultSet row = mock(ResultSet.class);
			for (int column = 1; column <= 7; column++) when(row.getLong(column)).thenReturn(0L);
			if (populated) {
				when(row.getLong(1)).thenReturn(11L); when(row.getLong(2)).thenReturn(12L);
				when(row.getLong(3)).thenReturn(14L); when(row.getLong(4)).thenReturn(20L);
				when(row.getLong(5)).thenReturn(25L); when(row.getLong(6)).thenReturn(3L);
				when(row.getLong(7)).thenReturn(4L);
			}
			return mapper.mapRow(row, 0);
		});
		when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
			String sql = invocation.getArgument(0);
			RowMapper mapper = invocation.getArgument(1);
			if (!populated) return List.of();
			ResultSet row = mock(ResultSet.class);
			if (sql.contains("JOIN quality_issues")) {
				when(row.getString(1)).thenReturn("TIMEOUT"); when(row.getString(2)).thenReturn("web");
				when(row.getString(3)).thenReturn("/api/slow"); when(row.getLong(4)).thenReturn(4L);
				when(row.getLong(5)).thenReturn(3L); when(row.getTimestamp(6)).thenReturn(Timestamp.from(EVENT_TIME));
				when(row.getString(7)).thenReturn("INVESTIGATING");
			}
			else if (sql.contains("GROUP BY LOWER(platform)")) {
				when(row.getString(1)).thenReturn("web"); when(row.getDouble(2)).thenReturn(250.0);
				when(row.getDouble(3)).thenReturn(4.0); when(row.getLong(4)).thenReturn(7L);
				when(row.getLong(5)).thenReturn(3L);
			}
			else {
				when(row.getTimestamp(1)).thenReturn(Timestamp.from(EVENT_TIME));
				when(row.getString(2)).thenReturn("user-1"); when(row.getString(3)).thenReturn("web");
				when(row.getString(4)).thenReturn("/home"); when(row.getString(5)).thenReturn("web.app_error");
				when(row.getString(6)).thenReturn("timeout"); when(row.getString(7)).thenReturn("/api/slow");
				when(row.getObject(8)).thenReturn(504); when(row.getString(9)).thenReturn("request-1");
			}
			return List.of(mapper.mapRow(row, 0));
		});
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
		return jdbc;
	}

	private static void respond(com.sun.net.httpserver.HttpExchange exchange,
			List<String> requests, String body) throws IOException {
		requests.add(URLDecoder.decode(exchange.getRequestURI().getRawQuery(),
				java.nio.charset.StandardCharsets.UTF_8));
		byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, bytes.length);
		try (var output = exchange.getResponseBody()) { output.write(bytes); }
	}
}
