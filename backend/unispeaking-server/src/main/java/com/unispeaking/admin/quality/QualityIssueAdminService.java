package com.unispeaking.admin.quality;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityIssueAdminService {
	private static final String ISSUE_SELECT = """
			SELECT q.*,
			       (SELECT COUNT(DISTINCT COALESCE(e.user_id::text, 'anon:' || e.anonymous_id))
			        FROM quality_issue_events e
			        WHERE e.issue_id = q.issue_id
			          AND (e.user_id IS NOT NULL OR NULLIF(e.anonymous_id, '') IS NOT NULL)) AS affected_users
			FROM quality_issues q
			""";

	private final JdbcTemplate jdbc;

	public QualityIssueAdminService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public QualitySummary summary() {
		return jdbc.queryForObject("""
				SELECT
				    COUNT(*) FILTER (WHERE status IN ('OPEN', 'INVESTIGATING', 'IN_PROGRESS')) AS active_issues,
				    COUNT(*) FILTER (WHERE severity = 'CRITICAL' AND status NOT IN ('VERIFIED', 'IGNORED')) AS critical_issues,
				    COUNT(*) FILTER (WHERE issue_type = 'OPTIMIZATION' AND status NOT IN ('VERIFIED', 'IGNORED')) AS optimizations,
				    COALESCE(SUM(occurrence_count) FILTER (WHERE last_seen_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'), 0) AS events_7d,
			    (SELECT COUNT(DISTINCT COALESCE(user_id::text, 'anon:' || anonymous_id))
			     FROM quality_issue_events
			     WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
			       AND (user_id IS NOT NULL OR NULLIF(anonymous_id, '') IS NOT NULL)) AS affected_users_7d,
				    COUNT(*) FILTER (WHERE status IN ('RESOLVED', 'VERIFIED')
				                     AND resolved_at >= CURRENT_TIMESTAMP - INTERVAL '7 days') AS resolved_7d
				FROM quality_issues
				""", (result, row) -> new QualitySummary(
				result.getLong("active_issues"),
				result.getLong("critical_issues"),
				result.getLong("optimizations"),
				result.getLong("events_7d"),
				result.getLong("affected_users_7d"),
				result.getLong("resolved_7d"),
				Instant.now()));
	}

	public IssueListResponse list(
			IssueStatus status,
			IssuePlatform platform,
			IssueType issueType,
			int requestedLimit) {
		int limit = Math.max(1, Math.min(requestedLimit, 200));
		StringBuilder sql = new StringBuilder(ISSUE_SELECT).append(" WHERE 1=1");
		var arguments = new java.util.ArrayList<Object>();
		if (status != null) {
			sql.append(" AND q.status = ?");
			arguments.add(status.name());
		}
		if (platform != null) {
			sql.append(" AND q.platform = ?");
			arguments.add(platform.name());
		}
		if (issueType != null) {
			sql.append(" AND q.issue_type = ?");
			arguments.add(issueType.name());
		}
		sql.append(" ORDER BY CASE q.severity WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END,")
				.append(" COALESCE(q.last_seen_at, q.updated_at) DESC LIMIT ?");
		arguments.add(limit);
		return new IssueListResponse(jdbc.query(sql.toString(), this::mapIssue, arguments.toArray()));
	}

	public IssueEventsResponse events(UUID issueId, int requestedLimit) {
		int limit = Math.max(1, Math.min(requestedLimit, 200));
		return new IssueEventsResponse(jdbc.query("""
				SELECT event_id, issue_id, user_id, anonymous_id, session_id, platform,
				       event_type, severity, release, route, message, api_path, api_method,
				       http_status, outcome, error_code, error_name, device_model, os_name,
				       os_version, network_type, duration_ms, occurred_at
				FROM quality_issue_events
				WHERE issue_id = ?
				ORDER BY occurred_at DESC
				LIMIT ?
				""", (result, row) -> new QualityEventView(
				result.getString("event_id"),
				result.getObject("issue_id", UUID.class),
				result.getObject("user_id") == null ? null : result.getObject("user_id", UUID.class),
				result.getString("anonymous_id"), result.getString("session_id"),
				IssuePlatform.valueOf(result.getString("platform")), result.getString("event_type"),
				result.getString("severity"), result.getString("release"), result.getString("route"),
				result.getString("message"), result.getString("api_path"), result.getString("api_method"),
				(Integer) result.getObject("http_status"), result.getString("outcome"),
				result.getString("error_code"), result.getString("error_name"), result.getString("device_model"),
				result.getString("os_name"), result.getString("os_version"), result.getString("network_type"),
				result.getObject("duration_ms") == null ? null : result.getDouble("duration_ms"),
				result.getTimestamp("occurred_at").toInstant()),
				issueId, limit));
	}

	@Transactional
	public QualityIssueView create(CreateIssueRequest request, UUID actorId, String actorLogin) {
		UUID issueId = UUID.randomUUID();
		Instant now = Instant.now();
		jdbc.update("""
				INSERT INTO quality_issues (
				    issue_id, issue_type, source, platform, severity, status, title,
				    description, assignee, occurrence_count, created_by, updated_by,
				    created_at, updated_at)
				VALUES (?, ?, 'MANUAL', ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
				""", issueId, request.issueType().name(), request.platform().name(),
				request.severity().name(), request.status().name(), request.title().trim(),
				trim(request.description()), trim(request.assignee()), actorLogin, actorLogin, now, now);
		insertHistory(issueId, "CREATED", null, request.status(), request.description(), actorId, actorLogin);
		return get(issueId);
	}

	@Transactional
	public QualityIssueView update(
			UUID issueId,
			UpdateIssueRequest request,
			UUID actorId,
			String actorLogin) {
		QualityIssueView before = get(issueId);
		IssueStatus nextStatus = request.status() == null ? before.status() : request.status();
		int updated = jdbc.update("""
				UPDATE quality_issues SET
				    issue_type = COALESCE(?, issue_type),
				    platform = COALESCE(?, platform),
				    severity = COALESCE(?, severity),
				    status = COALESCE(?, status),
				    title = COALESCE(?, title),
				    description = COALESCE(?, description),
				    assignee = COALESCE(?, assignee),
				    resolution = COALESCE(?, resolution),
				    resolved_at = CASE
				        WHEN ? IN ('RESOLVED', 'VERIFIED') THEN COALESCE(resolved_at, CURRENT_TIMESTAMP)
				        WHEN ? IS NOT NULL THEN NULL
				        ELSE resolved_at
				    END,
				    updated_by = ?, updated_at = CURRENT_TIMESTAMP
				WHERE issue_id = ?
				""",
				name(request.issueType()), name(request.platform()), name(request.severity()), name(request.status()),
				trim(request.title()), trim(request.description()), trim(request.assignee()), trim(request.resolution()),
				name(request.status()), name(request.status()), actorLogin, issueId);
		if (updated != 1) throw new QualityIssueNotFoundException(issueId);
		insertHistory(issueId, "UPDATED", before.status(), nextStatus,
				defaultText(request.note(), request.resolution()), actorId, actorLogin);
		return get(issueId);
	}

	public QualityIssueView get(UUID issueId) {
		List<QualityIssueView> issues = jdbc.query(
				ISSUE_SELECT + " WHERE q.issue_id = ?", this::mapIssue, issueId);
		if (issues.isEmpty()) throw new QualityIssueNotFoundException(issueId);
		return issues.getFirst();
	}

	private QualityIssueView mapIssue(ResultSet result, int row) throws SQLException {
		return new QualityIssueView(
				result.getObject("issue_id", UUID.class), result.getString("fingerprint"),
				IssueType.valueOf(result.getString("issue_type")), result.getString("source"),
				IssuePlatform.valueOf(result.getString("platform")),
				IssueSeverity.valueOf(result.getString("severity")),
				IssueStatus.valueOf(result.getString("status")), result.getString("title"),
				result.getString("description"), result.getString("error_code"), result.getString("api_path"),
				(Integer) result.getObject("http_status"), result.getString("release"), result.getString("assignee"),
				result.getString("resolution"), result.getLong("occurrence_count"), result.getLong("affected_users"),
				instant(result, "first_seen_at"), instant(result, "last_seen_at"), instant(result, "resolved_at"),
				result.getString("created_by"), result.getString("updated_by"),
				result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant());
	}

	private void insertHistory(
			UUID issueId,
			String action,
			IssueStatus from,
			IssueStatus to,
			String note,
			UUID actorId,
			String actorLogin) {
		jdbc.update("""
				INSERT INTO quality_issue_history (
				    issue_id, action, from_status, to_status, note, actor_admin_id, actor_login)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", issueId, action, name(from), name(to), trim(note), actorId, actorLogin);
	}

	private Instant instant(ResultSet result, String column) throws SQLException {
		return result.getTimestamp(column) == null ? null : result.getTimestamp(column).toInstant();
	}

	private String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

	private String trim(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String defaultText(String first, String second) {
		return trim(first) == null ? trim(second) : trim(first);
	}

	public enum IssueType { BUG, OPTIMIZATION }
	public enum IssuePlatform { WEB, MOBILE, BACKEND, CROSS_PLATFORM }
	public enum IssueSeverity { CRITICAL, HIGH, MEDIUM, LOW }
	public enum IssueStatus { OPEN, INVESTIGATING, IN_PROGRESS, RESOLVED, VERIFIED, IGNORED }

	public record QualitySummary(
			long activeIssues,
			long criticalIssues,
			long optimizations,
			long events7d,
			long affectedUsers7d,
			long resolved7d,
			Instant generatedAt) {}

	public record IssueListResponse(List<QualityIssueView> issues) {}
	public record IssueEventsResponse(List<QualityEventView> events) {}

	public record QualityIssueView(
			UUID issueId,
			String fingerprint,
			IssueType issueType,
			String source,
			IssuePlatform platform,
			IssueSeverity severity,
			IssueStatus status,
			String title,
			String description,
			String errorCode,
			String apiPath,
			Integer httpStatus,
			String release,
			String assignee,
			String resolution,
			long occurrenceCount,
			long affectedUsers,
			Instant firstSeenAt,
			Instant lastSeenAt,
			Instant resolvedAt,
			String createdBy,
			String updatedBy,
			Instant createdAt,
			Instant updatedAt) {}

	public record QualityEventView(
			String eventId,
			UUID issueId,
			UUID userId,
			String anonymousId,
			String sessionId,
			IssuePlatform platform,
			String eventType,
			String severity,
			String release,
			String route,
			String message,
			String apiPath,
			String apiMethod,
			Integer httpStatus,
			String outcome,
			String errorCode,
			String errorName,
			String deviceModel,
			String osName,
			String osVersion,
			String networkType,
			Double durationMs,
			Instant occurredAt) {}

	public record CreateIssueRequest(
			@NotNull IssueType issueType,
			@NotNull IssuePlatform platform,
			@NotNull IssueSeverity severity,
			@NotNull IssueStatus status,
			@NotBlank @Size(max = 200) String title,
			@Size(max = 8_000) String description,
			@Size(max = 120) String assignee) {}

	public record UpdateIssueRequest(
			IssueType issueType,
			IssuePlatform platform,
			IssueSeverity severity,
			IssueStatus status,
			@Size(max = 200) String title,
			@Size(max = 8_000) String description,
			@Size(max = 120) String assignee,
			@Size(max = 8_000) String resolution,
			@Size(max = 8_000) String note) {}

	public static final class QualityIssueNotFoundException extends RuntimeException {
		public QualityIssueNotFoundException(UUID issueId) {
			super("质量问题不存在：" + issueId);
		}
	}
}
