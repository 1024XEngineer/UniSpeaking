package com.unispeaking.admin.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MonitoringAdminServiceTest {

    private static final Instant EVENT_TIME = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    void returnsZeroedOverviewWhenPrometheusIsUnavailable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(null);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

        MonitoringAdminService.MonitoringResponse response = new MonitoringAdminService(
                jdbc, "http://127.0.0.1:1").overview();

        assertEquals("DOWN", response.summary().backendStatus());
        assertEquals(0, response.summary().clientErrorRate());
        assertEquals(0, response.summary().api5xxRate());
        assertEquals(3, response.platformSummaries().size());
        assertTrue(response.platformSummaries().stream()
                .allMatch(item -> item.affectedUsers() == 0 && item.errorCount() == 0));
        assertTrue(response.trend().isEmpty());
    }

    @Test
    void mapsPrometheusAndDatabaseOverviewRows() throws IOException {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class)))
                .thenReturn(11L, 12L, 13L);
        AtomicInteger queryNumber = new AtomicInteger();
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> mapper = invocation.getArgument(1);
            ResultSet row = mock(ResultSet.class);
            switch (queryNumber.getAndIncrement()) {
                case 0 -> {
                    when(row.getString(1)).thenReturn("TIMEOUT");
                    when(row.getString(2)).thenReturn("web");
                    when(row.getString(3)).thenReturn("/api/slow");
                    when(row.getLong(4)).thenReturn(4L);
                    when(row.getLong(5)).thenReturn(3L);
                    when(row.getTimestamp(6)).thenReturn(Timestamp.from(EVENT_TIME));
                }
                case 1 -> {
                    when(row.getString(1)).thenReturn("GET");
                    when(row.getString(2)).thenReturn("/api/slow");
                    when(row.getLong(3)).thenReturn(9L);
                    when(row.getDouble(4)).thenReturn(1.5);
                    when(row.getDouble(5)).thenReturn(2.5);
                    when(row.getDouble(6)).thenReturn(4.0);
                    when(row.getLong(7)).thenReturn(2L);
                }
                case 2 -> {
                    when(row.getTimestamp(1)).thenReturn(Timestamp.from(EVENT_TIME));
                    when(row.getString(2)).thenReturn("user-1");
                    when(row.getString(3)).thenReturn("web");
                    when(row.getString(4)).thenReturn("/home");
                    when(row.getString(5)).thenReturn("web.app_error");
                    when(row.getString(6)).thenReturn("timeout");
                    when(row.getString(7)).thenReturn("/api/slow");
                    when(row.getObject(8)).thenReturn(504);
                    when(row.getString(9)).thenReturn("request-1");
                }
                case 3 -> {
                    when(row.getString(1)).thenReturn("web");
                    when(row.getDouble(2)).thenReturn(250.0);
                    when(row.getDouble(3)).thenReturn(4.0);
                    when(row.getLong(4)).thenReturn(7L);
                    when(row.getLong(5)).thenReturn(3L);
                }
                default -> throw new AssertionError("unexpected query number");
            }
            return List.of(mapper.mapRow(row, 0));
        });

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/query", exchange -> respond(exchange,
                "{\"data\":{\"result\":[{\"value\":[\"0\",\"2.5\"]}]}}"));
        server.createContext("/api/v1/query_range", exchange -> respond(exchange,
                "{\"data\":{\"result\":[{\"values\":[[\"0\",\"1.0\"],[\"3600\",\"2.0\"]]}]}}"));
        server.start();
        try {
            MonitoringAdminService.MonitoringResponse response = new MonitoringAdminService(
                    jdbc, "http://127.0.0.1:" + server.getAddress().getPort()).overview();

            assertEquals("UP", response.summary().backendStatus());
            assertEquals(2.5, response.summary().clientErrorRate());
            assertEquals(11, response.summary().activeAlerts());
            assertEquals("TIMEOUT", response.problems().getFirst().problem());
            assertEquals(2.5, response.slowEndpoints().getFirst().p95Seconds());
            assertEquals("request-1", response.recentEvents().getFirst().requestId());
            assertEquals(3, response.platformSummaries().getFirst().errorCount());
            assertEquals(2, response.trend().size());
            assertEquals(2.0, response.trend().getLast().clientErrors());
        }
        finally {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
            throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
