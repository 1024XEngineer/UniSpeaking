package com.unispeaking.admin.monitoring;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MonitoringAdminService {
	private static final String API_REQUESTS = "http_route=~\"/api/.*\"";

	public record Summary(String backendStatus, double apiErrorRate5m, long api5xxCount24h,
			double apiP95Milliseconds24h, long activeAlerts, long affectedUsers24h,
			Instant generatedAt) {}
	public record Comparison(double previous, double current) {}
	public record Governance(long pendingIssues, long newBugs7d, long resolvedBugs7d,
			double bugFixRate, Comparison errorEvents, Comparison apiP95Milliseconds,
			Comparison affectedUsers) {}
	public record Problem(String problem, String platform, String path, long count,
			Double errorRate, long affectedUsers, Instant lastSeen, String status) {}
	public record PerformanceEndpoint(String method, String path,
			Double previousPeriodP95Milliseconds, Double currentPeriodP95Milliseconds,
			Double improvementRate, String status) {}
	public record Event(Instant timestamp, String userId, String platform, String page,
			String errorType, String errorMessage, String apiPath, Integer httpStatus,
			String requestId) {}
	public record PlatformSummary(String platform, double p95DurationMs,
			double requestFailureRate, long affectedUsers, long errorCount) {}
	public record TrendPoint(long timestamp, Double clientErrors,
			Double backendErrors, Double slowRequests) {}
	public record MonitoringResponse(Summary summary, Governance governance,
			List<Problem> problems, List<PerformanceEndpoint> performanceEndpoints,
			List<Event> recentEvents, List<PlatformSummary> platformSummaries,
			List<TrendPoint> trend) {}

	private enum MonitoringRange {
		ONE_HOUR("1h", 3600, 300, "5 minutes"),
		SIX_HOURS("6h", 6 * 3600, 900, "15 minutes"),
		ONE_DAY("24h", 24 * 3600, 3600, "1 hour"),
		SEVEN_DAYS("7d", 7 * 24 * 3600, 6 * 3600, "6 hours");

		private final String value;
		private final long seconds;
		private final long step;
		private final String bucket;

		MonitoringRange(String value, long seconds, long step, String bucket) {
			this.value = value;
			this.seconds = seconds;
			this.step = step;
			this.bucket = bucket;
		}

		static MonitoringRange from(String value) {
			for (MonitoringRange range : values()) {
				if (range.value.equals(value)) return range;
			}
			return ONE_DAY;
		}
	}

	private record Sample(long time, double value) {}
	private record EndpointKey(String method, String path) {}
	private record PerformanceBaseline(EndpointKey endpoint, double previousMilliseconds) {}
	private static final List<PerformanceBaseline> PERFORMANCE_BASELINES = List.of(
			new PerformanceBaseline(new EndpointKey("POST", "/api/custom-scenes/generate"), 14_500),
			new PerformanceBaseline(new EndpointKey("POST", "/api/ielts/{ieltsId}/sessions/{sessionId}/evaluation"), 22_500),
			new PerformanceBaseline(new EndpointKey("GET", "/api/custom-scenes/{sceneId}/sessions/{sessionId}/evaluation"), 18_800),
			new PerformanceBaseline(new EndpointKey("POST", "/api/ielts/{ieltsId}/sessions/{sessionId}/turns/{turnNo}/evaluation"), 8_600),
			new PerformanceBaseline(new EndpointKey("POST", "/api/custom-scenes/{sceneId}/sessions/{sessionId}/turns/{turnNo}/evaluation"), 8_200));
	private record GovernanceCounts(long pending, long created, long resolved,
			long currentEvents, long previousEvents, long currentUsers, long previousUsers) {}

	private final JdbcTemplate jdbc;
	private final RestClient prometheus;

	public MonitoringAdminService(JdbcTemplate jdbc,
			@Value("${unispeaking.monitoring.prometheus-url:${MONITORING_PROMETHEUS_URL:http://prometheus:9090}}") String prometheusUrl) {
		this.jdbc = jdbc;
		this.prometheus = RestClient.builder().baseUrl(prometheusUrl).build();
	}

	public MonitoringResponse overview(String requestedRange) {
		MonitoringRange range = MonitoringRange.from(requestedRange);
		double p95Current = queryPrometheus(p95Query("[24h]")) * 1000;
		double p95Previous = queryPrometheus(p95Query("[24h] offset 24h")) * 1000;
		GovernanceCounts counts = governanceCounts();
		Summary summary = new Summary("UP", queryPrometheus(apiErrorRateQuery()),
				Math.round(queryPrometheus(api5xxCountQuery())), p95Current,
				counts.pending(), counts.currentUsers(), Instant.now());
		double fixRate = counts.created() == 0 ? 0
				: 100.0 * counts.resolved() / counts.created();
		Governance governance = new Governance(counts.pending(), counts.created(),
				counts.resolved(), fixRate,
				new Comparison(counts.previousEvents(), counts.currentEvents()),
				new Comparison(p95Previous, p95Current),
				new Comparison(counts.previousUsers(), counts.currentUsers()));
		return new MonitoringResponse(summary, governance, problems(), performanceEndpoints(),
				recentEvents(), platformSummaries(), trend(range));
	}

	public MonitoringResponse overview() {
		return overview("24h");
	}

	private String apiErrorRateQuery() {
		return "(100 * sum(rate(http_server_request_duration_seconds_count{" + API_REQUESTS
				+ ",http_response_status_code=~\"4..|5..\"}[5m])) / clamp_min(sum(rate("
				+ "http_server_request_duration_seconds_count{" + API_REQUESTS
				+ "}[5m])), 0.000001)) or vector(0)";
	}

	private String api5xxCountQuery() {
		return "sum(increase(http_server_request_duration_seconds_count{" + API_REQUESTS
				+ ",http_response_status_code=~\"5..\"}[24h])) or vector(0)";
	}

	private String p95Query(String window) {
		return "histogram_quantile(0.95, sum by (le) (increase("
				+ "http_server_request_duration_seconds_bucket{" + API_REQUESTS + "}"
				+ window + "))) or vector(0)";
	}

	private GovernanceCounts governanceCounts() {
		GovernanceCounts counts = jdbc.queryForObject("""
				SELECT
				  COUNT(*) FILTER (WHERE status IN ('OPEN', 'INVESTIGATING', 'IN_PROGRESS')),
				  COUNT(*) FILTER (WHERE issue_type = 'BUG'
				      AND created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'),
				  COUNT(*) FILTER (WHERE issue_type = 'BUG'
				      AND status IN ('RESOLVED', 'VERIFIED')
				      AND resolved_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'),
				  (SELECT COUNT(*) FROM quality_issue_events
				      WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'),
				  (SELECT COUNT(*) FROM quality_issue_events
				      WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL '14 days'
				        AND occurred_at < CURRENT_TIMESTAMP - INTERVAL '7 days'),
				  (SELECT COUNT(DISTINCT COALESCE(user_id::text, 'anon:' || anonymous_id))
				      FROM quality_issue_events
				      WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
				        AND (user_id IS NOT NULL OR NULLIF(anonymous_id, '') IS NOT NULL)),
				  (SELECT COUNT(DISTINCT COALESCE(user_id::text, 'anon:' || anonymous_id))
				      FROM quality_issue_events
				      WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL '48 hours'
				        AND occurred_at < CURRENT_TIMESTAMP - INTERVAL '24 hours'
				        AND (user_id IS NOT NULL OR NULLIF(anonymous_id, '') IS NOT NULL))
				FROM quality_issues
				""", (rs, rowNum) -> new GovernanceCounts(rs.getLong(1), rs.getLong(2),
				rs.getLong(3), rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7)));
		return counts == null ? new GovernanceCounts(0, 0, 0, 0, 0, 0, 0) : counts;
	}

	private List<Problem> problems() {
		List<Problem> live = prometheusProblems();
		if (!live.isEmpty()) return live;
		Map<String, Double> rates = queryVector("100 * sum by (http_route) (increase("
				+ "http_server_request_duration_seconds_count{" + API_REQUESTS
				+ ",http_response_status_code=~\"4..|5..\"}[24h])) / clamp_min(sum by (http_route) "
				+ "(increase(http_server_request_duration_seconds_count{" + API_REQUESTS
				+ "}[24h])), 1)", "http_route");
		return jdbc.query("""
				SELECT COALESCE(e.error_code, e.error_name, e.message, 'HTTP ' || e.http_status::text),
				       LOWER(e.platform), COALESCE(e.api_path, e.route, ''), COUNT(*),
				       COUNT(DISTINCT COALESCE(e.user_id::text, 'anon:' || e.anonymous_id)),
				       MAX(e.occurred_at), q.status
				FROM quality_issue_events e
				JOIN quality_issues q ON q.issue_id = e.issue_id
				WHERE e.http_status >= 400
				  AND e.occurred_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
				GROUP BY 1, 2, 3, q.status
				ORDER BY COUNT(*) DESC, MAX(e.occurred_at) DESC LIMIT 10
				""", (rs, rowNum) -> {
			String path = rs.getString(3);
			return new Problem(rs.getString(1), rs.getString(2), path, rs.getLong(4),
					rates.get(path), rs.getLong(5), rs.getTimestamp(6).toInstant(),
					rs.getString(7));
		});
	}

	private List<Problem> prometheusProblems() {
		String query = "sum by (http_route) (increase(http_server_request_duration_seconds_count{"
				+ API_REQUESTS + ",http_response_status_code=~\"4..|5..\"}[24h]))";
		List<Problem> result = new ArrayList<>();
		for (Map<?, ?> item : instantVector(query)) {
			Object metric = item.get("metric");
			Object value = item.get("value");
			if (!(metric instanceof Map<?, ?> labels) || !(value instanceof List<?> pair) || pair.size() < 2) continue;
			String route = labels.get("http_route") == null ? "" : String.valueOf(labels.get("http_route"));
			long count = Math.round(finiteDouble(pair.get(1), 0));
			if (route.isBlank() || count <= 0) continue;
			result.add(new Problem("HTTP error", "backend", route, count, null, 0, Instant.now(), "OPEN"));
		}
		return result.stream().sorted((a, b) -> Long.compare(b.count(), a.count())).limit(5).toList();
	}

	private List<PerformanceEndpoint> performanceEndpoints() {
		String query = "histogram_quantile(0.95, sum by (le, http_route, http_request_method) "
				+ "(increase(http_server_request_duration_seconds_bucket{" + API_REQUESTS + "}%s)))";
		Map<EndpointKey, Double> current = queryEndpointVector(query.formatted("[24h]"));
		return PERFORMANCE_BASELINES.stream().map(baseline -> performanceEndpoint(
				baseline.endpoint(), baseline.previousMilliseconds(),
				milliseconds(current.get(baseline.endpoint())))).toList();
	}

	private PerformanceEndpoint performanceEndpoint(EndpointKey key, Double previous,
			Double current) {
		Double improvement = previous == null || previous <= 0 || current == null ? null
				: (previous - current) / previous * 100;
		String status;
		if (improvement == null) status = "PENDING";
		else if (improvement >= 20) status = "OPTIMIZED";
		else if (improvement > 0) status = "OBSERVING";
		else if (improvement <= -10) status = "REGRESSED";
		else status = "PENDING";
		return new PerformanceEndpoint(key.method(), key.path(), previous, current,
				improvement, status);
	}

	private Double milliseconds(Double seconds) {
		return seconds == null || !Double.isFinite(seconds) ? null : seconds * 1000;
	}

	private List<TrendPoint> trend(MonitoringRange range) {
		long end = Instant.now().getEpochSecond();
		long start = end - range.seconds;
		List<Sample> backendErrors = queryRange("sum(increase(http_server_request_duration_seconds_count{" + API_REQUESTS
				+ ",http_response_status_code=~\"5..\"}[5m])) or vector(0)", start, end, range.step);
		List<Sample> clientErrors = queryRange("sum(increase(http_server_request_duration_seconds_count{" + API_REQUESTS
				+ ",http_response_status_code=~\"4..\"}[5m])) or vector(0)", start, end, range.step);
		List<Sample> slowRequests = queryRange("clamp_min(sum(increase("
				+ "http_server_request_duration_seconds_count{" + API_REQUESTS + "}[5m])) - sum(increase("
				+ "http_server_request_duration_seconds_bucket{" + API_REQUESTS
				+ ",le=\"1.0\"}[5m])), 0) or vector(0)", start, end, range.step);
		Map<Long, Double> backendErrorValues = sampleMap(backendErrors);
		Map<Long, Double> clientErrorValues = sampleMap(clientErrors);
		Map<Long, Double> slowRequestValues = sampleMap(slowRequests);
		TreeSet<Long> times = new TreeSet<>();
		times.addAll(backendErrorValues.keySet());
		times.addAll(clientErrorValues.keySet());
		times.addAll(slowRequestValues.keySet());
		if (times.isEmpty()) return databaseTrend(range, start);
		return times.stream().map(time -> new TrendPoint(time, clientErrorValues.get(time),
				backendErrorValues.get(time), slowRequestValues.get(time))).toList();
	}

	private List<TrendPoint> databaseTrend(MonitoringRange range, long start) {
		String sql = """
			SELECT EXTRACT(EPOCH FROM date_bin(?::interval, occurred_at,
			           TIMESTAMPTZ '1970-01-01 00:00:00+00'))::bigint AS bucket,
			       COUNT(*) AS total,
			       COUNT(*) FILTER (WHERE http_status BETWEEN 400 AND 499) AS client_errors,
			       COUNT(*) FILTER (WHERE http_status >= 500) AS server_errors,
			       COUNT(DISTINCT COALESCE(user_id::text, 'anon:' || anonymous_id))
			           FILTER (WHERE user_id IS NOT NULL OR NULLIF(anonymous_id, '') IS NOT NULL) AS users
			FROM quality_issue_events
			WHERE occurred_at >= ?
			GROUP BY 1 ORDER BY 1
			""";
		return jdbc.query(sql, (rs, rowNum) -> {
			long client = rs.getLong("client_errors");
			long server = rs.getLong("server_errors");
			return new TrendPoint(rs.getLong("bucket"), (double) client, (double) server, null);
		}, range.bucket, Timestamp.from(Instant.ofEpochSecond(start)));
	}

	private List<Sample> affectedUserTrend(MonitoringRange range, long start) {
		return jdbc.query("""
				SELECT EXTRACT(EPOCH FROM date_bin(?::interval, occurred_at,
				           TIMESTAMPTZ '1970-01-01 00:00:00+00'))::bigint,
				       COUNT(DISTINCT COALESCE(user_id::text, 'anon:' || anonymous_id))
				FROM quality_issue_events
				WHERE occurred_at >= ?
				  AND (user_id IS NOT NULL OR NULLIF(anonymous_id, '') IS NOT NULL)
				GROUP BY 1 ORDER BY 1
				""", (rs, rowNum) -> new Sample(rs.getLong(1), rs.getDouble(2)),
				range.bucket, Timestamp.from(Instant.ofEpochSecond(start)));
	}

	private Map<Long, Double> sampleMap(List<Sample> samples) {
		Map<Long, Double> values = new HashMap<>();
		for (Sample sample : samples) values.put(sample.time(), sample.value());
		return values;
	}

	private double queryPrometheus(String query) {
		try {
			Map<?, ?> body = prometheus.get().uri("/api/v1/query?query={query}", query)
					.retrieve().body(Map.class);
			Object data = body == null ? null : body.get("data");
			if (data instanceof Map<?, ?> map && map.get("result") instanceof List<?> results
					&& !results.isEmpty() && results.getFirst() instanceof Map<?, ?> result
					&& result.get("value") instanceof List<?> value && value.size() > 1) {
				return finiteDouble(value.get(1), 0);
			}
		}
		catch (RuntimeException ignored) { }
		return 0;
	}

	private Map<String, Double> queryVector(String query, String label) {
		Map<String, Double> values = new LinkedHashMap<>();
		for (Map<?, ?> result : instantVector(query)) {
			Object metric = result.get("metric");
			Object value = result.get("value");
			if (metric instanceof Map<?, ?> labels && value instanceof List<?> pair
					&& pair.size() > 1 && labels.get(label) != null) {
				values.put(String.valueOf(labels.get(label)), finiteDouble(pair.get(1), 0));
			}
		}
		return values;
	}

	private Map<EndpointKey, Double> queryEndpointVector(String query) {
		Map<EndpointKey, Double> values = new LinkedHashMap<>();
		for (Map<?, ?> result : instantVector(query)) {
			Object metric = result.get("metric");
			Object value = result.get("value");
			if (metric instanceof Map<?, ?> labels && value instanceof List<?> pair
					&& pair.size() > 1 && labels.get("http_route") != null) {
				Object methodLabel = labels.get("http_request_method");
				String method = methodLabel == null ? "" : String.valueOf(methodLabel);
				values.put(new EndpointKey(method, String.valueOf(labels.get("http_route"))),
						finiteDouble(pair.get(1), 0));
			}
		}
		return values;
	}

	private List<Map<?, ?>> instantVector(String query) {
		try {
			Map<?, ?> body = prometheus.get().uri("/api/v1/query?query={query}", query)
					.retrieve().body(Map.class);
			Object data = body == null ? null : body.get("data");
			if (data instanceof Map<?, ?> map && map.get("result") instanceof List<?> results) {
				List<Map<?, ?>> mapped = new ArrayList<>();
				for (Object result : results) if (result instanceof Map<?, ?> item) mapped.add(item);
				return mapped;
			}
		}
		catch (RuntimeException ignored) { }
		return List.of();
	}

	private List<Sample> queryRange(String query, long start, long end, long step) {
		try {
			Map<?, ?> body = prometheus.get().uri(
					"/api/v1/query_range?query={query}&start={start}&end={end}&step={step}",
					query, start, end, step).retrieve().body(Map.class);
			Object data = body == null ? null : body.get("data");
			if (data instanceof Map<?, ?> map && map.get("result") instanceof List<?> results
					&& !results.isEmpty() && results.getFirst() instanceof Map<?, ?> result
					&& result.get("values") instanceof List<?> values) {
				return values.stream().filter(List.class::isInstance).map(List.class::cast)
						.filter(value -> value.size() > 1)
						.map(value -> new Sample(Math.round(finiteDouble(value.get(0), 0)),
								finiteDouble(value.get(1), 0))).toList();
			}
		}
		catch (RuntimeException ignored) { }
		return List.of();
	}

	private double finiteDouble(Object value, double fallback) {
		try {
			double parsed = Double.parseDouble(String.valueOf(value));
			return Double.isFinite(parsed) ? parsed : fallback;
		}
		catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private List<PlatformSummary> platformSummaries() {
		List<PlatformSummary> summaries = jdbc.query("""
				SELECT LOWER(platform),
				       COALESCE(percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms)
				           FILTER (WHERE duration_ms IS NOT NULL), 0),
				       COALESCE(100.0 * COUNT(*) FILTER (WHERE
				           http_status >= 400 OR LOWER(COALESCE(severity, '')) IN ('error', 'fatal')
				           OR LOWER(COALESCE(outcome, '')) IN ('error', 'network_error', 'timeout'))
				           / NULLIF(COUNT(*), 0), 0),
				       COUNT(DISTINCT COALESCE(user_id::text, 'anon:' || anonymous_id))
				           FILTER (WHERE (user_id IS NOT NULL OR NULLIF(anonymous_id, '') IS NOT NULL)
				             AND (http_status >= 400 OR LOWER(COALESCE(severity, '')) IN ('error', 'fatal')
				               OR LOWER(COALESCE(outcome, '')) IN ('error', 'network_error', 'timeout'))),
				       COUNT(*) FILTER (WHERE http_status >= 400
				           OR LOWER(COALESCE(severity, '')) IN ('error', 'fatal')
				           OR LOWER(COALESCE(outcome, '')) IN ('error', 'network_error', 'timeout'))
				FROM quality_issue_events
				WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
				GROUP BY LOWER(platform)
				""", (rs, rowNum) -> new PlatformSummary(rs.getString(1), rs.getDouble(2),
				rs.getDouble(3), rs.getLong(4), rs.getLong(5)));
		List<PlatformSummary> result = new ArrayList<>(3);
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
				FROM quality_issue_events WHERE
				  (http_status >= 400
				   OR LOWER(COALESCE(severity, '')) IN ('error', 'fatal')
				   OR LOWER(COALESCE(outcome, '')) IN ('error', 'network_error', 'timeout'))
				  AND occurred_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
				ORDER BY occurred_at DESC LIMIT 20
				""", (rs, rowNum) -> new Event(rs.getTimestamp(1).toInstant(), rs.getString(2),
				rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
				rs.getString(7), (Integer) rs.getObject(8), rs.getString(9)));
	}
}
