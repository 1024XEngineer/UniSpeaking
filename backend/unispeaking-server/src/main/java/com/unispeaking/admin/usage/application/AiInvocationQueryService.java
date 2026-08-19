package com.unispeaking.admin.usage.application;

import com.unispeaking.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public final class AiInvocationQueryService {
	private static final String SYSTEM_USER_KEY = "";
	private static final String DISPLAY_RECORD_SQL = "(i.capability <> 'REALTIME' or "
			+ "(i.business_scene = 'realtime_session' and i.usage_source = 'OFFICIAL'))";

	private final JdbcTemplate jdbc;

	public AiInvocationQueryService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public UsageResponse query(Query query) {
		return query(query, 1);
	}

	public UsageResponse query(Query query, int page) {
		Query normalized = (query == null ? new Query(null, null, null, null, null, 10) : query).normalized();
		SqlFilter filter = filter(normalized);
		Summary summary = summary(filter);
		List<ModelSummary> byModel = byModel(filter);
		Map<String, List<UserModelSummary>> modelsByUser = modelsByUser(filter);
		List<UserSummary> byUser = byUser(filter, modelsByUser);
		RequestIdCoverage requestIdCoverage = requestIdCoverage(filter);
		long totalRecords = recordCount(filter);
		int totalPages = totalRecords == 0 ? 0 : (int) Math.ceil((double) totalRecords / normalized.limit());
		int normalizedPage = totalPages == 0 ? 1 : Math.min(Math.max(1, page), totalPages);
		int offset = Math.multiplyExact(normalizedPage - 1, normalized.limit());
		List<InvocationRecord> records = records(filter, normalized.limit(), offset);
		return new UsageResponse(normalized, summary, byModel, byUser, records,
				requestIdCoverage,
				new RecordPage(normalizedPage, normalized.limit(), totalRecords, totalPages));
	}

	private long recordCount(SqlFilter filter) {
		Long count = jdbc.queryForObject(
				"select count(*) from ai_model_invocations i where " + filter.sql()
						+ " and " + DISPLAY_RECORD_SQL,
				Long.class,
				filter.arguments().toArray());
		return count == null ? 0 : count;
	}

	private RequestIdCoverage requestIdCoverage(SqlFilter filter) {
		String eligible = "lower(i.provider_id) <> 'iflytek' "
				+ "and not (i.status = 'SUCCEEDED' and i.usage_source = 'NONE') and " + DISPLAY_RECORD_SQL;
		return jdbc.queryForObject(
				"select count(*) filter (where " + eligible + ") eligible_records, "
						+ "count(*) filter (where " + eligible
						+ " and i.provider_request_id is not null and i.provider_request_id <> '') records_with_id "
						+ "from ai_model_invocations i where " + filter.sql(),
				(rs, row) -> new RequestIdCoverage(
						rs.getLong("records_with_id"), rs.getLong("eligible_records")),
				filter.arguments().toArray());
	}

	private Summary summary(SqlFilter filter) {
		return jdbc.queryForObject(
				"select count(*) attempts, count(distinct i.logical_request_id) requests, "
						+ "count(*) filter (where i.status='SUCCEEDED') succeeded_attempts, "
						+ "count(*) filter (where i.attempt_no > 1) fallback_attempts, "
						+ "coalesce(sum(i.input_tokens),0) input_tokens, coalesce(sum(i.output_tokens),0) output_tokens, "
						+ "coalesce(sum(i.total_tokens),0) total_tokens, coalesce(sum(i.audio_input_seconds),0) audio_input_seconds, "
						+ "coalesce(sum(i.audio_output_seconds),0) audio_output_seconds, coalesce(avg(i.duration_ms),0) average_duration_ms, "
						+ "coalesce(sum(i.estimated_cost),0) estimated_cost from ai_model_invocations i where " + filter.sql(),
				(rs, row) -> new Summary(rs.getLong("requests"), rs.getLong("attempts"),
						rs.getLong("succeeded_attempts"), rs.getLong("fallback_attempts"),
						rs.getLong("input_tokens"), rs.getLong("output_tokens"), rs.getLong("total_tokens"),
						rs.getBigDecimal("audio_input_seconds"), rs.getBigDecimal("audio_output_seconds"),
						rs.getBigDecimal("average_duration_ms"), rs.getBigDecimal("estimated_cost"), "CNY"),
				filter.arguments().toArray());
	}

	private List<ModelSummary> byModel(SqlFilter filter) {
		return jdbc.query(
				"select i.provider_id, i.model_id, i.capability, count(*) attempts, "
						+ "count(*) filter (where i.status='SUCCEEDED') successes, coalesce(sum(i.total_tokens),0) total_tokens, "
						+ "coalesce(avg(i.duration_ms),0) average_duration_ms, coalesce(sum(i.estimated_cost),0) estimated_cost "
						+ "from ai_model_invocations i where " + filter.sql()
						+ " group by i.provider_id, i.model_id, i.capability order by estimated_cost desc, attempts desc",
				(rs, row) -> new ModelSummary(rs.getString("provider_id"), rs.getString("model_id"),
						rs.getString("capability"), rs.getLong("attempts"), rs.getLong("successes"),
						rs.getLong("total_tokens"), rs.getBigDecimal("average_duration_ms"),
						rs.getBigDecimal("estimated_cost")), filter.arguments().toArray());
	}

	private Map<String, List<UserModelSummary>> modelsByUser(SqlFilter filter) {
		Map<String, List<UserModelSummary>> result = new LinkedHashMap<>();
		jdbc.query(
				"select i.user_id, i.provider_id, i.model_id, i.capability, "
						+ "count(distinct i.logical_request_id) requests, count(*) attempts, "
						+ "count(*) filter (where i.status='SUCCEEDED') successes, "
						+ "coalesce(sum(i.input_tokens),0) input_tokens, coalesce(sum(i.output_tokens),0) output_tokens, "
						+ "coalesce(sum(i.total_tokens),0) total_tokens, coalesce(sum(i.audio_input_seconds),0) audio_input_seconds, "
						+ "coalesce(sum(i.audio_output_seconds),0) audio_output_seconds, coalesce(sum(i.duration_ms),0) total_duration_ms, "
						+ "coalesce(sum(i.estimated_cost),0) estimated_cost from ai_model_invocations i where " + filter.sql()
						+ " group by i.user_id, i.provider_id, i.model_id, i.capability "
						+ "order by estimated_cost desc, attempts desc",
				(rs) -> {
					String userId = string(rs.getObject("user_id"));
					result.computeIfAbsent(userKey(userId), ignored -> new ArrayList<>()).add(new UserModelSummary(
							rs.getString("provider_id"), rs.getString("model_id"), rs.getString("capability"),
							rs.getLong("requests"), rs.getLong("attempts"), rs.getLong("successes"),
							rs.getLong("input_tokens"), rs.getLong("output_tokens"), rs.getLong("total_tokens"),
							rs.getBigDecimal("audio_input_seconds"), rs.getBigDecimal("audio_output_seconds"),
							rs.getLong("total_duration_ms"), rs.getBigDecimal("estimated_cost")));
				}, filter.arguments().toArray());
		return result;
	}

	private List<UserSummary> byUser(SqlFilter filter, Map<String, List<UserModelSummary>> modelsByUser) {
		return jdbc.query(
				"select i.user_id, u.username email, count(distinct i.logical_request_id) requests, "
						+ "count(distinct i.session_id) sessions, count(*) attempts, "
						+ "count(*) filter (where i.status='SUCCEEDED') successes, "
						+ "count(*) filter (where i.status='FAILED') failures, "
						+ "count(*) filter (where i.attempt_no > 1) fallback_attempts, "
						+ "coalesce(sum(i.input_tokens),0) input_tokens, coalesce(sum(i.output_tokens),0) output_tokens, "
						+ "coalesce(sum(i.total_tokens),0) total_tokens, coalesce(sum(i.input_characters),0) input_characters, "
						+ "coalesce(sum(i.output_characters),0) output_characters, "
						+ "coalesce(sum(i.audio_input_seconds),0) audio_input_seconds, "
						+ "coalesce(sum(i.audio_output_seconds),0) audio_output_seconds, "
						+ "coalesce(sum(i.duration_ms),0) total_duration_ms, coalesce(avg(i.duration_ms),0) average_duration_ms, "
						+ "coalesce(sum(i.estimated_cost),0) estimated_cost, max(i.started_at) last_invoked_at "
						+ "from ai_model_invocations i left join users u on u.id=i.user_id where " + filter.sql()
						+ " group by i.user_id, u.username order by estimated_cost desc, attempts desc",
				(rs, row) -> {
					String userId = string(rs.getObject("user_id"));
					return new UserSummary(userId, rs.getString("email"), rs.getLong("requests"),
							rs.getLong("sessions"), rs.getLong("attempts"), rs.getLong("successes"),
							rs.getLong("failures"), rs.getLong("fallback_attempts"), rs.getLong("input_tokens"),
							rs.getLong("output_tokens"), rs.getLong("total_tokens"), rs.getLong("input_characters"),
							rs.getLong("output_characters"), rs.getBigDecimal("audio_input_seconds"),
							rs.getBigDecimal("audio_output_seconds"), rs.getLong("total_duration_ms"),
							rs.getBigDecimal("average_duration_ms"), rs.getBigDecimal("estimated_cost"),
							rs.getObject("last_invoked_at", OffsetDateTime.class),
							modelsByUser.getOrDefault(userKey(userId), List.of()));
				}, filter.arguments().toArray());
	}

	private List<InvocationRecord> records(SqlFilter filter, int limit, int offset) {
		List<Object> arguments = new ArrayList<>(filter.arguments());
		arguments.add(limit);
		arguments.add(offset);
		return jdbc.query(
				"select i.invocation_id, i.logical_request_id, i.attempt_no, i.user_id, u.username user_email, "
						+ "i.session_id, i.business_scene, i.route_key, i.capability, i.provider_id, i.model_id, "
						+ "i.provider_request_id, i.started_at, i.completed_at, i.duration_ms, i.first_token_latency_ms, "
						+ "i.input_tokens, i.output_tokens, i.total_tokens, i.input_characters, i.output_characters, "
						+ "i.audio_input_seconds, i.audio_output_seconds, i.usage_source, i.status, i.error_code, "
						+ "i.retryable, i.fallback_from_model_id, i.estimated_cost, i.price_currency "
						+ "from ai_model_invocations i left join users u on u.id=i.user_id where " + filter.sql()
						+ " and " + DISPLAY_RECORD_SQL
						+ " order by i.started_at desc limit ? offset ?",
				(rs, row) -> new InvocationRecord(
						rs.getObject("invocation_id", UUID.class), rs.getObject("logical_request_id", UUID.class),
						rs.getInt("attempt_no"), string(rs.getObject("user_id")), rs.getString("user_email"),
						rs.getString("session_id"), rs.getString("business_scene"), rs.getString("route_key"),
						rs.getString("capability"), rs.getString("provider_id"), rs.getString("model_id"),
						rs.getString("provider_request_id"), rs.getObject("started_at", OffsetDateTime.class),
						rs.getObject("completed_at", OffsetDateTime.class), rs.getLong("duration_ms"),
						rs.getObject("first_token_latency_ms", Long.class), rs.getLong("input_tokens"),
						rs.getLong("output_tokens"), rs.getLong("total_tokens"), rs.getLong("input_characters"),
						rs.getLong("output_characters"), rs.getBigDecimal("audio_input_seconds"),
						rs.getBigDecimal("audio_output_seconds"), rs.getString("usage_source"), rs.getString("status"),
						rs.getString("error_code"), rs.getBoolean("retryable"), rs.getString("fallback_from_model_id"),
						rs.getBigDecimal("estimated_cost"), rs.getString("price_currency")), arguments.toArray());
	}

	private SqlFilter filter(Query query) {
		StringBuilder sql = new StringBuilder("i.started_at >= ? and i.started_at < ?");
		List<Object> arguments = new ArrayList<>();
		arguments.add(query.from());
		arguments.add(query.to());
		add(sql, arguments, "i.user_id", query.userId());
		add(sql, arguments, "i.provider_id", query.providerId());
		add(sql, arguments, "i.model_id", query.modelId());
		return new SqlFilter(sql.toString(), arguments);
	}

	private void add(StringBuilder sql, List<Object> arguments, String column, String value) {
		if (value == null || value.isBlank()) return;
		sql.append(" and ").append(column).append(" = ?");
		if (!"i.user_id".equals(column)) {
			arguments.add(value.trim());
			return;
		}
		try {
			arguments.add(UUID.fromString(value.trim()));
		}
		catch (IllegalArgumentException exception) {
			throw new BusinessException("AI_USAGE_USER_ID_INVALID", "用户 ID 必须是 UUID");
		}
	}

	private static String userKey(String userId) { return userId == null ? SYSTEM_USER_KEY : userId; }
	private static String string(Object value) { return value == null ? null : value.toString(); }

	public record Query(OffsetDateTime from, OffsetDateTime to, String userId, String providerId, String modelId, Integer limit) {
		Query normalized() {
			OffsetDateTime normalizedTo = to == null ? OffsetDateTime.now(ZoneOffset.UTC) : to;
			OffsetDateTime normalizedFrom = from == null ? normalizedTo.minusDays(30) : from;
			if (!normalizedFrom.isBefore(normalizedTo)) {
				throw new BusinessException("AI_USAGE_TIME_RANGE_INVALID", "开始时间必须早于结束时间");
			}
			return new Query(normalizedFrom, normalizedTo, userId, providerId, modelId,
					limit == null ? 10 : Math.max(1, Math.min(100, limit)));
		}
	}

	public record Summary(long requests, long attempts, long succeededAttempts, long fallbackAttempts,
			long inputTokens, long outputTokens, long totalTokens, BigDecimal audioInputSeconds,
			BigDecimal audioOutputSeconds, BigDecimal averageDurationMs, BigDecimal estimatedCost, String currency) {}

	public record ModelSummary(String providerId, String modelId, String capability, long attempts,
			long successes, long totalTokens, BigDecimal averageDurationMs, BigDecimal estimatedCost) {}

	public record UserModelSummary(String providerId, String modelId, String capability, long requests,
			long attempts, long successes, long inputTokens, long outputTokens, long totalTokens,
			BigDecimal audioInputSeconds, BigDecimal audioOutputSeconds, long totalDurationMs,
			BigDecimal estimatedCost) {}

	public record UserSummary(String userId, String email, long requests, long sessions, long attempts,
			long successes, long failures, long fallbackAttempts, long inputTokens, long outputTokens,
			long totalTokens, long inputCharacters, long outputCharacters, BigDecimal audioInputSeconds,
			BigDecimal audioOutputSeconds, long totalDurationMs, BigDecimal averageDurationMs,
			BigDecimal estimatedCost, OffsetDateTime lastInvokedAt, List<UserModelSummary> models) {}

	public record InvocationRecord(UUID invocationId, UUID logicalRequestId, int attemptNo, String userId,
			String userEmail, String sessionId, String businessScene, String routeKey, String capability,
			String providerId, String modelId, String providerRequestId, OffsetDateTime startedAt,
			OffsetDateTime completedAt, long durationMs, Long firstTokenLatencyMs, long inputTokens,
			long outputTokens, long totalTokens, long inputCharacters, long outputCharacters,
			BigDecimal audioInputSeconds, BigDecimal audioOutputSeconds, String usageSource, String status,
			String errorCode, boolean retryable, String fallbackFromModelId, BigDecimal estimatedCost,
			String priceCurrency) {}

	public record RecordPage(int page, int pageSize, long totalRecords, int totalPages) {}
	public record RequestIdCoverage(long recordsWithRequestId, long eligibleRecords) {}

	public record UsageResponse(Query query, Summary summary, List<ModelSummary> byModel,
			List<UserSummary> byUser, List<InvocationRecord> records,
			RequestIdCoverage requestIdCoverage, RecordPage recordPage) {}

	private record SqlFilter(String sql, List<Object> arguments) {}
}
