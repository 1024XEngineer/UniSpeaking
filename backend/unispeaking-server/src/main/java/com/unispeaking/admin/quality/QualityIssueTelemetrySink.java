package com.unispeaking.admin.quality;

import com.unispeaking.telemetry.ClientTelemetryRecord;
import com.unispeaking.telemetry.ClientTelemetrySink;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class QualityIssueTelemetrySink implements ClientTelemetrySink {
	private final JdbcTemplate jdbc;
	private final ObjectMapper objectMapper;

	public QualityIssueTelemetrySink(JdbcTemplate jdbc, ObjectMapper objectMapper) {
		this.jdbc = jdbc;
		this.objectMapper = objectMapper;
	}

	@Override
	@Transactional
	public void write(ClientTelemetryRecord record) {
		Map<String, Object> fields = record.fields();
		if (!isActionable(fields)) return;

		String platform = normalizedPlatform(text(fields, "platform"));
		String eventType = defaultText(text(fields, "event_type"), "client.error");
		String message = text(fields, "message");
		String apiPath = text(fields, "api_path");
		Integer httpStatus = integer(fields, "http_status");
		String errorCode = text(fields, "error_code");
		String release = text(fields, "release");
		Instant occurredAt = instant(fields, "occurred_at");
		String severity = issueSeverity(text(fields, "level"), httpStatus);
		String fingerprint = fingerprint(platform, eventType, apiPath, httpStatus, errorCode,
				text(fields, "error_name"), message);

		UUID issueId = jdbc.queryForObject("""
				INSERT INTO quality_issues (
				    fingerprint, issue_type, source, platform, severity, status, title,
				    description, error_code, api_path, http_status, release,
				    occurrence_count, first_seen_at, last_seen_at, created_by, updated_by)
				VALUES (?, 'BUG', 'TELEMETRY', ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, 0, ?, ?, 'telemetry', 'telemetry')
				ON CONFLICT (fingerprint) WHERE fingerprint IS NOT NULL DO UPDATE SET
				    severity = CASE
				        WHEN quality_issues.severity = 'CRITICAL' OR EXCLUDED.severity = 'CRITICAL' THEN 'CRITICAL'
				        WHEN quality_issues.severity = 'HIGH' OR EXCLUDED.severity = 'HIGH' THEN 'HIGH'
				        WHEN quality_issues.severity = 'MEDIUM' OR EXCLUDED.severity = 'MEDIUM' THEN 'MEDIUM'
				        ELSE 'LOW'
				    END,
				    release = COALESCE(EXCLUDED.release, quality_issues.release),
				    error_code = COALESCE(EXCLUDED.error_code, quality_issues.error_code),
				    api_path = COALESCE(EXCLUDED.api_path, quality_issues.api_path),
				    http_status = COALESCE(EXCLUDED.http_status, quality_issues.http_status),
				    last_seen_at = GREATEST(quality_issues.last_seen_at, EXCLUDED.last_seen_at),
				    updated_at = CURRENT_TIMESTAMP,
				    updated_by = 'telemetry'
				RETURNING issue_id
				""", UUID.class,
				fingerprint, platform, severity, title(eventType, apiPath, httpStatus, message), message,
				errorCode, apiPath, httpStatus, release, occurredAt, occurredAt);

		int inserted = insertEvent(issueId, fields, platform, eventType, occurredAt);
		if (inserted == 1) {
			jdbc.update("""
					UPDATE quality_issues SET
					    occurrence_count = occurrence_count + 1,
					    first_seen_at = COALESCE(first_seen_at, ?),
					    last_seen_at = GREATEST(COALESCE(last_seen_at, ?), ?),
					    status = CASE WHEN status IN ('RESOLVED', 'VERIFIED') THEN 'OPEN' ELSE status END,
					    resolved_at = CASE WHEN status IN ('RESOLVED', 'VERIFIED') THEN NULL ELSE resolved_at END,
					    updated_at = CURRENT_TIMESTAMP
					WHERE issue_id = ?
					""", occurredAt, occurredAt, occurredAt, issueId);
		}
	}

	@Transactional
	public void captureBackend(
			Throwable error,
			String route,
			String method,
			int status,
			String errorCode) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("event_id", UUID.randomUUID().toString());
		fields.put("event_type", "backend.exception");
		fields.put("platform", "backend");
		fields.put("level", status >= 500 ? "error" : "warn");
		fields.put("occurred_at", Instant.now().toString());
		fields.put("route", route);
		fields.put("api_path", route);
		fields.put("api_method", method);
		fields.put("http_status", status);
		fields.put("error_code", errorCode);
		fields.put("error_name", error.getClass().getSimpleName());
		fields.put("message", error.getMessage());
		fields.put("stack", stackSummary(error));
		String authenticatedUserId = authenticatedUserId();
		if (authenticatedUserId != null) fields.put("user_id", authenticatedUserId);
		write(new ClientTelemetryRecord(fields));
	}

	private String authenticatedUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) return null;
		String subject = jwt.getSubject();
		try {
			return subject == null ? null : UUID.fromString(subject).toString();
		}
		catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private int insertEvent(
			UUID issueId,
			Map<String, Object> fields,
			String platform,
			String eventType,
			Instant occurredAt) {
		return jdbc.update("""
				INSERT INTO quality_issue_events (
				    event_id, issue_id, user_id, anonymous_id, session_id, platform,
				    event_type, severity, release, route, message, stack, api_path,
				    api_method, http_status, outcome, error_code, error_name, device_model,
				    os_name, os_version, network_type, duration_ms, attributes, occurred_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
				ON CONFLICT (event_id) DO NOTHING
				""",
				defaultText(text(fields, "event_id"), UUID.randomUUID().toString()), issueId,
				uuid(fields, "user_id"), text(fields, "anonymous_id"), text(fields, "session_id"),
				platform, eventType, normalizedEventSeverity(text(fields, "level")), text(fields, "release"),
				text(fields, "route"), text(fields, "message"), text(fields, "stack"), text(fields, "api_path"),
				text(fields, "api_method"), integer(fields, "http_status"), text(fields, "outcome"),
				text(fields, "error_code"), text(fields, "error_name"), text(fields, "device_model"),
				text(fields, "os_name"), text(fields, "os_version"), text(fields, "network_type"),
				number(fields, "duration_ms"), attributesJson(fields), occurredAt);
	}

	private boolean isActionable(Map<String, Object> fields) {
		Integer status = integer(fields, "http_status");
		String outcome = lower(text(fields, "outcome"));
		if ((status != null && status == 401) || "unauthenticated".equals(outcome)) return false;
		String level = lower(text(fields, "level"));
		return "error".equals(level) || "fatal".equals(level)
				|| "error".equals(outcome) || "network_error".equals(outcome) || "timeout".equals(outcome);
	}

	private String attributesJson(Map<String, Object> fields) {
		try {
			return objectMapper.writeValueAsString(fields);
		}
		catch (JacksonException exception) {
			return "{}";
		}
	}

	private String fingerprint(Object... parts) {
		StringBuilder canonical = new StringBuilder();
		for (Object part : parts) canonical.append('|').append(part == null ? "" : part);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String title(String eventType, String apiPath, Integer status, String message) {
		String value = apiPath == null
				? defaultText(message, eventType)
				: defaultText(textValue(status), "请求失败") + " · " + apiPath;
		return value.length() <= 200 ? value : value.substring(0, 200);
	}

	private String issueSeverity(String level, Integer status) {
		if ("fatal".equals(lower(level))) return "CRITICAL";
		if (status != null && status >= 500) return "HIGH";
		return "MEDIUM";
	}

	private String normalizedEventSeverity(String value) {
		String severity = defaultText(value, "error").toUpperCase(Locale.ROOT);
		return switch (severity) {
			case "INFO", "WARN", "ERROR", "FATAL" -> severity;
			default -> "ERROR";
		};
	}

	private String normalizedPlatform(String value) {
		String platform = defaultText(value, "backend").toUpperCase(Locale.ROOT);
		return switch (platform) {
			case "WEB", "MOBILE", "BACKEND" -> platform;
			default -> "BACKEND";
		};
	}

	private String stackSummary(Throwable error) {
		StringBuilder stack = new StringBuilder(error.toString());
		for (StackTraceElement element : error.getStackTrace()) {
			if (stack.length() >= 8_000) break;
			stack.append('\n').append("\tat ").append(element);
		}
		return stack.length() <= 8_000 ? stack.toString() : stack.substring(0, 8_000);
	}

	private UUID uuid(Map<String, Object> fields, String key) {
		try {
			String value = text(fields, key);
			return value == null ? null : UUID.fromString(value);
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private Instant instant(Map<String, Object> fields, String key) {
		try {
			String value = text(fields, key);
			return value == null ? Instant.now() : Instant.parse(value);
		}
		catch (RuntimeException exception) {
			return Instant.now();
		}
	}

	private Integer integer(Map<String, Object> fields, String key) {
		Object value = fields.get(key);
		if (value instanceof Number number) return number.intValue();
		try {
			return value == null ? null : Integer.valueOf(value.toString());
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	private Double number(Map<String, Object> fields, String key) {
		Object value = fields.get(key);
		if (value instanceof Number number) return number.doubleValue();
		try {
			return value == null ? null : Double.valueOf(value.toString());
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	private String text(Map<String, Object> fields, String key) {
		Object value = fields.get(key);
		return value == null || value.toString().isBlank() ? null : value.toString().trim();
	}

	private String lower(String value) {
		return value == null ? null : value.toLowerCase(Locale.ROOT);
	}

	private String defaultText(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private String textValue(Integer value) {
		return value == null ? null : value.toString();
	}
}
