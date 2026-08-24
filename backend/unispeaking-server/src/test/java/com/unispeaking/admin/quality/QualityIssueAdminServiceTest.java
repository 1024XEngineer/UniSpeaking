package com.unispeaking.admin.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.unispeaking.admin.quality.QualityIssueAdminService.IssuePlatform;
import com.unispeaking.admin.quality.QualityIssueAdminService.IssueSeverity;
import com.unispeaking.admin.quality.QualityIssueAdminService.IssueStatus;
import com.unispeaking.admin.quality.QualityIssueAdminService.IssueType;
import com.unispeaking.admin.quality.QualityIssueAdminService.QualityIssueNotFoundException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class QualityIssueAdminServiceTest {

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
	private final QualityIssueAdminService service = new QualityIssueAdminService(jdbc);

	@Test
	void listClampsLimitAndAddsRequestedFilters() {
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		var response = service.list(IssueStatus.OPEN, IssuePlatform.BACKEND, IssueType.BUG, 999);

		assertEquals(List.of(), response.issues());
		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).query(anyString(), any(RowMapper.class), arguments.capture());
		assertEquals(List.of("OPEN", "BACKEND", "BUG", 200), List.of(arguments.getValue()));
	}

	@Test
	void eventsClampsLimitAndMapsNullableColumns() throws Exception {
		UUID issueId = UUID.randomUUID();
		when(jdbc.query(anyString(), any(RowMapper.class), eq(issueId), anyInt())).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked") RowMapper<QualityIssueAdminService.QualityEventView> mapper = invocation.getArgument(1);
			return List.of(mapper.mapRow(eventResult(issueId), 0));
		});

		var response = service.events(issueId, 0);

		assertNull(response.events().getFirst().userId());
		assertNull(response.events().getFirst().httpStatus());
		assertNull(response.events().getFirst().durationMs());
		verify(jdbc).query(anyString(), any(RowMapper.class), eq(issueId), eq(1));
	}

	@Test
	void summaryMapsAggregateColumns() throws Exception {
		when(jdbc.queryForObject(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked") RowMapper<QualityIssueAdminService.QualitySummary> mapper = invocation.getArgument(1);
			ResultSet result = mock(ResultSet.class);
			when(result.getLong("active_issues")).thenReturn(2L);
			when(result.getLong("critical_issues")).thenReturn(1L);
			when(result.getLong("optimizations")).thenReturn(3L);
			when(result.getLong("events_7d")).thenReturn(4L);
			when(result.getLong("affected_users_7d")).thenReturn(5L);
			when(result.getLong("resolved_7d")).thenReturn(6L);
			return mapper.mapRow(result, 0);
		});

		var summary = service.summary();

		assertEquals(2, summary.activeIssues());
		assertEquals(6, summary.resolved7d());
	}

	@Test
	void createWritesIssueAndHistoryThenReturnsPersistedResult() {
		UUID actor = UUID.randomUUID();
		when(jdbc.query(anyString(), any(RowMapper.class), any(UUID.class))).thenAnswer(invocation ->
				List.of(issue(invocation.getArgument(2))));

		var result = service.create(new QualityIssueAdminService.CreateIssueRequest(
				IssueType.BUG, IssuePlatform.WEB, IssueSeverity.HIGH, IssueStatus.OPEN,
				"  Broken form  ", " details ", " owner "), actor, "admin");

		assertEquals("Broken form", result.title());
		verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), any(Object[].class));
	}

	@Test
	void updateAndGetRejectUnknownIssues() {
		UUID issueId = UUID.randomUUID();
		when(jdbc.query(anyString(), any(RowMapper.class), eq(issueId))).thenReturn(List.of());

		assertThrows(QualityIssueNotFoundException.class, () -> service.get(issueId));
		assertThrows(QualityIssueNotFoundException.class, () -> service.update(issueId,
				new QualityIssueAdminService.UpdateIssueRequest(null, null, null, null, null, null, null, null, null),
				UUID.randomUUID(), "admin"));
	}

	@Test
	void listWithoutFiltersClampsNegativeLimitAndPreservesQueryArguments() {
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

		assertEquals(List.of(), service.list(null, null, null, -10).issues());
		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).query(anyString(), any(RowMapper.class), arguments.capture());
		assertEquals(List.of(1), List.of(arguments.getValue()));
	}

	@Test
	void getMapsEveryIssueColumnAndNullableTimestamps() throws Exception {
		UUID issueId = UUID.randomUUID();
		Instant now = Instant.parse("2026-08-21T00:00:00Z");
		when(jdbc.query(anyString(), any(RowMapper.class), eq(issueId))).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked") RowMapper<QualityIssueAdminService.QualityIssueView> mapper = invocation.getArgument(1);
			ResultSet result = mock(ResultSet.class);
			when(result.getObject("issue_id", UUID.class)).thenReturn(issueId);
			when(result.getString("fingerprint")).thenReturn("fp");
			when(result.getString("issue_type")).thenReturn("OPTIMIZATION");
			when(result.getString("source")).thenReturn("TELEMETRY");
			when(result.getString("platform")).thenReturn("CROSS_PLATFORM");
			when(result.getString("severity")).thenReturn("CRITICAL");
			when(result.getString("status")).thenReturn("VERIFIED");
			when(result.getString("title")).thenReturn("title");
			when(result.getString("description")).thenReturn("description");
			when(result.getString("error_code")).thenReturn("E");
			when(result.getString("api_path")).thenReturn("/api");
			when(result.getObject("http_status")).thenReturn(500);
			when(result.getString("release")).thenReturn("1.0");
			when(result.getString("assignee")).thenReturn("admin");
			when(result.getString("resolution")).thenReturn("fixed");
			when(result.getLong("occurrence_count")).thenReturn(4L);
			when(result.getLong("affected_users")).thenReturn(3L);
			when(result.getTimestamp("first_seen_at")).thenReturn(Timestamp.from(now));
			when(result.getTimestamp("last_seen_at")).thenReturn(Timestamp.from(now.plusSeconds(1)));
			when(result.getTimestamp("resolved_at")).thenReturn(null);
			when(result.getString("created_by")).thenReturn("creator");
			when(result.getString("updated_by")).thenReturn("updater");
			when(result.getTimestamp("created_at")).thenReturn(Timestamp.from(now));
			when(result.getTimestamp("updated_at")).thenReturn(Timestamp.from(now.plusSeconds(2)));
			return List.of(mapper.mapRow(result, 0));
		});

		var issue = service.get(issueId);

		assertEquals(IssueType.OPTIMIZATION, issue.issueType());
		assertEquals(IssuePlatform.CROSS_PLATFORM, issue.platform());
		assertEquals(IssueSeverity.CRITICAL, issue.severity());
		assertEquals(IssueStatus.VERIFIED, issue.status());
		assertEquals(3, issue.affectedUsers());
		assertEquals(now.plusSeconds(2), issue.updatedAt());
		assertNull(issue.resolvedAt());
	}

	@Test
	void updateWritesTrimmedOptionalValuesAndHistoryThenReturnsNextRecord() {
		UUID issueId = UUID.randomUUID();
		var before = issue(issueId);
		var after = new QualityIssueAdminService.QualityIssueView(issueId, "fingerprint", IssueType.BUG,
				"MANUAL", IssuePlatform.BACKEND, IssueSeverity.CRITICAL, IssueStatus.RESOLVED, "new title",
				"new description", "ERR", "/api", 500, "2.0", "owner", "fixed", 2, 1, null, null, null,
				"admin", "admin", Instant.now(), Instant.now());
		when(jdbc.query(anyString(), any(RowMapper.class), eq(issueId))).thenReturn(List.of(before), List.of(after));
		when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

		var result = service.update(issueId,
				new QualityIssueAdminService.UpdateIssueRequest(IssueType.BUG, IssuePlatform.BACKEND,
						IssueSeverity.CRITICAL, IssueStatus.RESOLVED, "  new title ", " details ", " owner ",
						" fixed ", " note "), UUID.randomUUID(), "admin");

		assertEquals("new title", result.title());
		assertEquals(2, result.occurrenceCount());
		verify(jdbc, times(2)).update(anyString(), any(Object[].class));
	}

	@Test
	void updateThrowsWhenTheDatabaseDoesNotUpdateTheIssue() {
		UUID issueId = UUID.randomUUID();
		when(jdbc.query(anyString(), any(RowMapper.class), eq(issueId))).thenReturn(List.of(issue(issueId)));
		when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

		assertThrows(QualityIssueNotFoundException.class, () -> service.update(issueId,
				new QualityIssueAdminService.UpdateIssueRequest(null, null, null, null, null, null, null, null, null),
				UUID.randomUUID(), "admin"));
	}

	private QualityIssueAdminService.QualityIssueView issue(UUID issueId) {
		Instant now = Instant.parse("2026-01-01T00:00:00Z");
		return new QualityIssueAdminService.QualityIssueView(issueId, "fingerprint", IssueType.BUG,
				"MANUAL", IssuePlatform.WEB, IssueSeverity.HIGH, IssueStatus.OPEN, "Broken form",
				"details", null, null, null, null, null, null, 0, 0, null, null, null,
				"admin", "admin", now, now);
	}

	private ResultSet eventResult(UUID issueId) throws Exception {
		ResultSet result = mock(ResultSet.class);
		when(result.getString("event_id")).thenReturn("event");
		when(result.getObject("issue_id", UUID.class)).thenReturn(issueId);
		when(result.getObject("user_id")).thenReturn(null);
		when(result.getString("platform")).thenReturn("WEB");
		when(result.getTimestamp("occurred_at")).thenReturn(Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
		return result;
	}
}
