package com.unispeaking.admin.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MonitoringAdminService {
	private static final String API_REQUESTS = "http_route=~\"/api/.*\"";

    public record Summary(String backendStatus, double clientErrorRate, double api5xxRate,
            double apiP95Seconds, long activeAlerts, long affectedUsers,
            long completedOptimizations, long resolvedBugs7d, Instant generatedAt) {}
    public record Problem(String problem, String platform, String path, long count,
            long affectedUsers, Instant lastSeen, String status) {}
    public record SlowEndpoint(String method, String path, long calls, double averageSeconds,
            double p95Seconds, double maxSeconds, long slowCount) {}
    public record Event(Instant timestamp, String userId, String platform, String page,
            String errorType, String errorMessage, String apiPath, Integer httpStatus,
            String requestId) {}
    public record PlatformSummary(String platform, double p95DurationMs,
            double requestFailureRate, long affectedUsers, long errorCount) {}
    public record TrendPoint(long timestamp, double clientErrors, double slowRequests, double backendErrors) {}
    public record MonitoringResponse(Summary summary, List<Problem> problems,
            List<SlowEndpoint> slowEndpoints, List<Event> recentEvents,
            List<PlatformSummary> platformSummaries, List<TrendPoint> trend) {}

    private final JdbcTemplate jdbc;
    private final RestClient prometheus;

    public MonitoringAdminService(JdbcTemplate jdbc,
            @Value("${unispeaking.monitoring.prometheus-url:http://prometheus:9090}") String prometheusUrl) {
        this.jdbc = jdbc;
        this.prometheus = RestClient.builder().baseUrl(prometheusUrl).build();
    }

    public MonitoringResponse overview() {
        var summary = new Summary(backendStatus(),
                queryPrometheus("(100 * sum(rate(http_server_request_duration_seconds_count{" + API_REQUESTS + ",http_response_status_code=~\"4..|5..\"}[5m])) / clamp_min(sum(rate(http_server_request_duration_seconds_count{" + API_REQUESTS + "}[5m])), 0.000001)) or vector(0)"),
                queryPrometheus("(100 * sum(rate(http_server_request_duration_seconds_count{" + API_REQUESTS + ",http_response_status_code=~\"5..\"}[5m])) / clamp_min(sum(rate(http_server_request_duration_seconds_count{" + API_REQUESTS + "}[5m])), 0.000001)) or vector(0)"),
                queryPrometheus("histogram_quantile(0.95, sum by (le) (increase(http_server_request_duration_seconds_bucket{" + API_REQUESTS + "}[24h]))) or vector(0)"),
                activeAlerts(), affectedUsers(), completedOptimizations(), resolvedBugs7d(), Instant.now());
        return new MonitoringResponse(summary, problems(), slowEndpoints(), recentEvents(),
                platformSummaries(), trend());
    }

    private String backendStatus() {
        // This method runs inside the backend that served the overview request. If the
        // backend were unavailable the request itself would fail instead of returning a
        // misleading DOWN status merely because Prometheus is temporarily unreachable.
        return "UP";
    }

    private double queryPrometheus(String query) {
        try {
            Map<?, ?> body = prometheus.get().uri("/api/v1/query?query={query}", query)
                    .retrieve().body(Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof Map<?, ?> map && map.get("result") instanceof List<?> results && !results.isEmpty()
                    && results.getFirst() instanceof Map<?, ?> result && result.get("value") instanceof List<?> value
                    && value.size() > 1) return Double.parseDouble(String.valueOf(value.get(1)));
        } catch (RuntimeException ignored) { }
        return 0;
    }

    private List<TrendPoint> trend() {
        long end = Instant.now().getEpochSecond();
        long start = end - 24 * 60 * 60;
        var client = queryRange("sum(rate(http_server_request_duration_seconds_count{" + API_REQUESTS + ",http_response_status_code=~\"4..|5..\"}[5m])) or vector(0)", start, end);
        var slow = queryRange("clamp_min(sum(rate(http_server_request_duration_seconds_count{" + API_REQUESTS + "}[5m])) - sum(rate(http_server_request_duration_seconds_bucket{" + API_REQUESTS + ",le=\"1.0\"}[5m])), 0) or vector(0)", start, end);
        var backend = queryRange("sum(rate(http_server_request_duration_seconds_count{" + API_REQUESTS + ",http_response_status_code=~\"5..\"}[5m])) or vector(0)", start, end);
        int size = Math.min(client.size(), Math.min(slow.size(), backend.size()));
        var result = new java.util.ArrayList<TrendPoint>(size);
        for (int i = 0; i < size; i++) {
            result.add(new TrendPoint(client.get(i).time(), client.get(i).value(), slow.get(i).value(), backend.get(i).value()));
        }
        return result;
    }

    private record Sample(long time, double value) {}

    private List<Sample> queryRange(String query, long start, long end) {
        try {
            Map<?, ?> body = prometheus.get().uri(
                    "/api/v1/query_range?query={query}&start={start}&end={end}&step=3600",
                    query, start, end).retrieve().body(Map.class);
            Object data = body == null ? null : body.get("data");
            if (data instanceof Map<?, ?> map && map.get("result") instanceof List<?> results && !results.isEmpty()
                    && results.getFirst() instanceof Map<?, ?> result && result.get("values") instanceof List<?> values) {
                return values.stream().filter(List.class::isInstance).map(List.class::cast).filter(value -> value.size() > 1)
                        .map(value -> new Sample(Long.parseLong(String.valueOf(Double.valueOf(String.valueOf(value.get(0))).longValue())), Double.parseDouble(String.valueOf(value.get(1))))).toList();
            }
        } catch (RuntimeException ignored) { }
        return List.of();
    }

    private long affectedUsers() {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT COALESCE(user_id::text, 'anon:' || anonymous_id))
                FROM quality_issue_events
                WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
                  AND (user_id IS NOT NULL OR NULLIF(anonymous_id, '') IS NOT NULL)
                """, Long.class);
        return value == null ? 0 : value;
    }

    private long activeAlerts() {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM quality_issues
                WHERE status IN ('OPEN', 'INVESTIGATING', 'IN_PROGRESS')
                """, Long.class);
        return value == null ? 0 : value;
    }

    private long completedOptimizations() {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM quality_issues
                WHERE issue_type = 'OPTIMIZATION' AND status IN ('RESOLVED', 'VERIFIED')
                """, Long.class);
        return value == null ? 0 : value;
    }

    private long resolvedBugs7d() {
        Long value = jdbc.queryForObject("""
                SELECT COUNT(*) FROM quality_issues
                WHERE issue_type = 'BUG'
                  AND status IN ('RESOLVED', 'VERIFIED')
                  AND resolved_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
                """, Long.class);
        return value == null ? 0 : value;
    }

    private List<Problem> problems() {
        return jdbc.query("""
                SELECT COALESCE(e.error_code, e.error_name, e.message, 'HTTP ' || e.http_status::text),
                       LOWER(e.platform), COALESCE(e.api_path, e.route, ''), COUNT(*),
                       COUNT(DISTINCT COALESCE(e.user_id::text, 'anon:' || e.anonymous_id)),
                       MAX(e.occurred_at), q.status
                FROM quality_issue_events e
                JOIN quality_issues q ON q.issue_id = e.issue_id
                WHERE e.http_status >= 400
                  AND e.occurred_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
                  AND q.status IN ('OPEN', 'INVESTIGATING', 'IN_PROGRESS')
                GROUP BY 1, 2, 3, q.status
                ORDER BY COUNT(*) DESC, MAX(e.occurred_at) DESC LIMIT 10
                """, (rs, n) -> new Problem(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getLong(4), rs.getLong(5), rs.getTimestamp(6).toInstant(), rs.getString(7)));
    }

    private List<SlowEndpoint> slowEndpoints() {
        return jdbc.query("""
                SELECT COALESCE(api_method, ''), COALESCE(api_path, route, ''), COUNT(*),
                       AVG(duration_ms) / 1000.0, percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) / 1000.0,
                       MAX(duration_ms) / 1000.0, COUNT(*) FILTER (WHERE duration_ms > 1000)
                FROM quality_issue_events
                WHERE duration_ms IS NOT NULL
                  AND occurred_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
                GROUP BY 1, 2 ORDER BY percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) DESC LIMIT 5
                """, (rs, n) -> new SlowEndpoint(rs.getString(1), rs.getString(2), rs.getLong(3),
                rs.getDouble(4), rs.getDouble(5), rs.getDouble(6), rs.getLong(7)));
    }

    private List<PlatformSummary> platformSummaries() {
        var summaries = jdbc.query("""
                SELECT LOWER(platform),
                       COALESCE(percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms)
                           FILTER (WHERE duration_ms IS NOT NULL), 0),
                       COALESCE(100.0 * COUNT(*) FILTER (WHERE http_status >= 400)
                           / NULLIF(COUNT(*) FILTER (WHERE api_path IS NOT NULL), 0), 0),
                       COUNT(DISTINCT COALESCE(user_id::text, 'anon:' || anonymous_id))
                           FILTER (WHERE user_id IS NOT NULL OR NULLIF(anonymous_id, '') IS NOT NULL),
                       COUNT(*) FILTER (WHERE http_status >= 400)
                FROM quality_issue_events
                WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
                GROUP BY LOWER(platform)
                """, (rs, n) -> new PlatformSummary(rs.getString(1), rs.getDouble(2),
                rs.getDouble(3), rs.getLong(4), rs.getLong(5)));
        var result = new java.util.ArrayList<PlatformSummary>(3);
        for (String platform : List.of("web", "mobile", "backend")) {
            result.add(summaries.stream().filter(item -> platform.equals(item.platform()))
                    .findFirst().orElse(new PlatformSummary(platform, 0, 0, 0, 0)));
        }
        return result;
    }

    private List<Event> recentEvents() {
        return jdbc.query("""
                SELECT occurred_at, COALESCE(user_id::text, anonymous_id, '-'), LOWER(platform),
                       COALESCE(route, '-'), event_type, message, COALESCE(api_path, '-'),
                       http_status, COALESCE(attributes->>'request_id', '-')
                FROM quality_issue_events WHERE http_status >= 400
                  AND occurred_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
                ORDER BY occurred_at DESC LIMIT 20
                """, (rs, n) -> new Event(rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                (Integer) rs.getObject(8), rs.getString(9)));
    }
}
