package com.unispeaking.admin.usage.adapters.jdbc;

import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.ProviderStatus;
import com.unispeaking.admin.usage.domain.UsageSnapshot;
import com.unispeaking.admin.usage.domain.UsageSession;
import com.unispeaking.admin.usage.domain.UsageUser;
import com.unispeaking.admin.usage.ports.UsageDataSource;
import com.unispeaking.admin.usage.application.UsageSourceUnavailableException;
import java.time.LocalDate;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "unispeaking.integrations.usage-source", havingValue = "postgres", matchIfMissing = true)
public final class JdbcUsageDataSource implements UsageDataSource {
    private static final ModelUsage ZERO_USAGE = new ModelUsage(0, 0, 0, 0, 0, 0, 0, 0);
    private final JdbcTemplate jdbc;

    public JdbcUsageDataSource(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UsageSnapshot loadSnapshot() {
        try {
            var users = jdbc.query(
                        "select u.id, u.username email, coalesce(e.plan_code, 'free') plan_code, "
                        + "coalesce(e.plan_name, 'Free') plan_name, current_date quota_date, "
                        + "coalesce(e.status, 'active') status, coalesce(e.quota_seconds, 600) quota_seconds, "
                        + "case when e.quota_date = current_date then coalesce(e.used_seconds, 0) else 0 end used_seconds "
                        + "from \"user\" u left join user_entitlements e "
                        + "on e.user_id = u.id "
                        + "order by u.created_at desc",
                (rs, row) -> {
                    var quota = rs.getDouble("quota_seconds");
                    var entitlementUsed = rs.getDouble("used_seconds");
                    var userId = rs.getObject("id", java.util.UUID.class).toString();
                    var planCode = rs.getString("plan_code");
                    var quotaDate = rs.getObject("quota_date", LocalDate.class);
                    var sessions = loadSessions(userId, planCode, quota, quotaDate);
                    var measured = sessions.stream().mapToDouble(UsageSession::measuredSeconds).sum();
                    var used = Math.max(entitlementUsed, measured);
                    var activeSession = sessions.stream()
                            .filter(session -> session.status().equals("created")
                                    || session.status().equals("connecting")
                                    || session.status().equals("waiting_client")
                                    || session.status().equals("active"))
                            .findFirst()
                            .map(UsageSession::sessionId)
                            .orElse(null);
                    return new UsageUser(
                            userId, rs.getString("email"), planCode, rs.getString("plan_name"), rs.getString("status"),
                            quotaDate.toString(), quota, used, 0, used,
                            Math.max(0, quota - used), null, activeSession, sessions.size(), sessions, ZERO_USAGE, ZERO_USAGE,
                            "0",
                            reconciliationCounts(sessions));
                });
            return new UsageSnapshot(users, null, new ProviderStatus(null, Map.of(), List.of(), 0, "postgres", null, false));
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new UsageSourceUnavailableException("PostgreSQL 用户用量数据源不可用", exception);
        }
    }

    private List<UsageSession> loadSessions(String userId, String planCode, double quota, LocalDate quotaDate) {
        return jdbc.query(
                    "select session_id, status, started_at, ended_at, provider_session_id from practice_session where user_id = ? "
                            + "and coalesce(ended_at, started_at) >= ? and coalesce(ended_at, started_at) < ? "
                            + "order by started_at desc limit 100",
                    (rs, row) -> {
                        OffsetDateTime started = rs.getObject("started_at", OffsetDateTime.class);
                        OffsetDateTime ended = rs.getObject("ended_at", OffsetDateTime.class);
                        double seconds = ended == null ? 0 : Math.max(0, Duration.between(started, ended).toMillis() / 1000d);
                        String status = rs.getString("status").toLowerCase(java.util.Locale.ROOT);
                        String taskUuid = rs.getString("provider_session_id");
                        OfficialSummary official = loadOfficialSummary(taskUuid);
                        return new UsageSession(
                                rs.getString("session_id"), userId, planCode, status, seconds,
                                Math.max(0, quota - seconds), null, null, null,
                                taskUuid, official == null ? null : official.requestId(),
                                ZERO_USAGE, official == null ? ZERO_USAGE : official.usage(),
                                official == null ? null : official.durationMs(), "0", "UNAVAILABLE",
                                official == null ? "PENDING" : "MATCHED",
                                official == null
                                        ? List.of("official SLS usage has not been imported for this provider session")
                                        : List.of(),
                                null);
                    }, java.util.UUID.fromString(userId), quotaDate, quotaDate.plusDays(1));
    }

    private OfficialSummary loadOfficialSummary(String taskUuid) {
        if (taskUuid == null || taskUuid.isBlank()) return null;
        return jdbc.queryForObject(
                    "select count(*) response_count, coalesce(sum(total_tokens), 0) total_tokens, "
                            + "coalesce(sum(input_tokens), 0) input_tokens, coalesce(sum(output_tokens), 0) output_tokens, "
                            + "coalesce(sum(input_text_tokens), 0) input_text_tokens, "
                            + "coalesce(sum(input_audio_tokens), 0) input_audio_tokens, "
                            + "coalesce(sum(output_text_tokens), 0) output_text_tokens, "
                            + "coalesce(sum(output_audio_tokens), 0) output_audio_tokens, "
                            + "coalesce(sum(duration_ms), 0) duration_ms, max(request_id) request_id "
                            + "from official_usage_records where task_uuid = ?",
                    (rs, row) -> {
                        long responseCount = rs.getLong("response_count");
                        if (responseCount == 0) return null;
                        return new OfficialSummary(
                                new ModelUsage(
                                        responseCount,
                                        rs.getLong("total_tokens"),
                                        rs.getLong("input_tokens"),
                                        rs.getLong("output_tokens"),
                                        rs.getLong("input_text_tokens"),
                                        rs.getLong("input_audio_tokens"),
                                        rs.getLong("output_text_tokens"),
                                        rs.getLong("output_audio_tokens")),
                                rs.getLong("duration_ms"),
                                rs.getString("request_id"));
                    },
                    taskUuid);
    }

    private static Map<String, Integer> reconciliationCounts(List<UsageSession> sessions) {
        Map<String, Integer> counts = new java.util.HashMap<>(Map.of("PENDING", 0, "MATCHED", 0, "MISMATCH", 0));
        sessions.forEach(session -> counts.compute(session.reconciliationStatus(), (key, value) -> value + 1));
        return counts;
    }

    private record OfficialSummary(ModelUsage usage, long durationMs, String requestId) {}
}
