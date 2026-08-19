package com.unispeaking.admin.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AiInvocationQueryServiceTest {

	@Test
	void returnsCompleteAttemptsAndUserModelAggregationFromTheSameLedger() {
		JdbcTemplate jdbc = database();
		var service = new AiInvocationQueryService(jdbc);

		var response = service.query(new AiInvocationQueryService.Query(
				OffsetDateTime.parse("2026-08-18T00:00:00Z"),
				OffsetDateTime.parse("2026-08-19T00:00:00Z"), null, null, null, 100));

		assertThat(response.summary().requests()).isEqualTo(1);
		assertThat(response.summary().attempts()).isEqualTo(2);
		assertThat(response.summary().fallbackAttempts()).isEqualTo(1);
		assertThat(response.summary().estimatedCost()).isEqualByComparingTo("0.03000000");
		assertThat(response.records()).hasSize(2);
		assertThat(response.records().getFirst().userEmail()).isEqualTo("learner@example.com");
		assertThat(response.records().getFirst().completedAt()).isNotNull();
		assertThat(response.records().getFirst().firstTokenLatencyMs()).isEqualTo(120);
		assertThat(response.recordPage().page()).isEqualTo(1);
		assertThat(response.recordPage().pageSize()).isEqualTo(100);
		assertThat(response.recordPage().totalRecords()).isEqualTo(2);
		assertThat(response.recordPage().totalPages()).isEqualTo(1);

		assertThat(response.byUser()).singleElement().satisfies(user -> {
			assertThat(user.email()).isEqualTo("learner@example.com");
			assertThat(user.requests()).isEqualTo(1);
			assertThat(user.sessions()).isEqualTo(1);
			assertThat(user.attempts()).isEqualTo(2);
			assertThat(user.successes()).isEqualTo(1);
			assertThat(user.failures()).isEqualTo(1);
			assertThat(user.fallbackAttempts()).isEqualTo(1);
			assertThat(user.totalTokens()).isEqualTo(150);
			assertThat(user.models()).extracting(AiInvocationQueryService.UserModelSummary::modelId)
					.containsExactly("deepseek-v4-flash", "qwen3.5-plus");
		});
	}

	@Test
	void pagesInvocationRecordsWithoutChangingTheCompleteSummary() {
		JdbcTemplate jdbc = database();
		var service = new AiInvocationQueryService(jdbc);
		var query = new AiInvocationQueryService.Query(
				OffsetDateTime.parse("2026-08-18T00:00:00Z"),
				OffsetDateTime.parse("2026-08-19T00:00:00Z"), null, null, null, 1);

		var response = service.query(query, 2);

		assertThat(response.summary().attempts()).isEqualTo(2);
		assertThat(response.records()).singleElement()
				.extracting(AiInvocationQueryService.InvocationRecord::modelId)
				.isEqualTo("qwen3.5-plus");
		assertThat(response.recordPage()).isEqualTo(new AiInvocationQueryService.RecordPage(2, 1, 2, 2));
	}

	@Test
	void pagesOnlyMatchedRealtimeRecordsAndReportsFullRangeRequestIdCoverage() {
		JdbcTemplate jdbc = database();
		jdbc.update("""
				insert into ai_model_invocations values
				('20000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000003', 1,
				 '10000000-0000-0000-0000-000000000001', null, 'pronunciation_scoring', 'default', 'SCORING',
				 'iflytek', 'iflytek-suntone', null, '2026-08-18T10:00:00Z', '2026-08-18T10:00:00.100Z',
				 100, null, 0, 0, 0, 10, 0, 1, 0, 'ESTIMATED', 'SUCCEEDED', null, false, null, 0.00500000, 'CNY'),
					('20000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000004', 1,
					 '10000000-0000-0000-0000-000000000001', 'session-2', 'realtime_session', 'default', 'REALTIME',
					 'qwen', 'qwen-realtime', 'handshake-only', '2026-08-18T11:00:00Z', '2026-08-18T11:01:00Z',
					 60000, null, 0, 0, 0, 0, 0, 0, 0, 'ESTIMATED', 'SUCCEEDED', null, false, null, 0, 'CNY'),
					('20000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000005', 1,
					 '10000000-0000-0000-0000-000000000001', null, 'tts', 'default', 'TTS',
					 'qwen', 'qwen3-tts-flash', null, '2026-08-18T12:00:00Z', '2026-08-18T12:00:00.010Z',
					 10, null, 0, 0, 0, 0, 0, 0, 0, 'NONE', 'SUCCEEDED', null, false, null, 0, 'CNY')
					""");
		var service = new AiInvocationQueryService(jdbc);

		var response = service.query(new AiInvocationQueryService.Query(
				OffsetDateTime.parse("2026-08-18T00:00:00Z"),
				OffsetDateTime.parse("2026-08-19T00:00:00Z"), null, null, null, 100));

		assertThat(response.summary().attempts()).isEqualTo(5);
		assertThat(response.recordPage().totalRecords()).isEqualTo(4);
		assertThat(response.records()).extracting(AiInvocationQueryService.InvocationRecord::modelId)
				.containsExactly("qwen3-tts-flash", "iflytek-suntone", "deepseek-v4-flash", "qwen3.5-plus");
		assertThat(response.requestIdCoverage())
				.isEqualTo(new AiInvocationQueryService.RequestIdCoverage(2, 2));
	}

	private static JdbcTemplate database() {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:ai-invocation-query-" + java.util.UUID.randomUUID()
				+ ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("""
				create table users (
				    id uuid primary key,
				    username varchar(320) not null
				)
				""");
		jdbc.execute("""
				create table ai_model_invocations (
				    invocation_id uuid primary key,
				    logical_request_id uuid not null,
				    attempt_no integer not null,
				    user_id uuid,
				    session_id varchar(64),
				    business_scene varchar(64) not null,
				    route_key varchar(64) not null,
				    capability varchar(32) not null,
				    provider_id varchar(64) not null,
				    model_id varchar(128) not null,
				    provider_request_id varchar(256),
				    started_at timestamp with time zone not null,
				    completed_at timestamp with time zone not null,
				    duration_ms bigint not null,
				    first_token_latency_ms bigint,
				    input_tokens bigint not null,
				    output_tokens bigint not null,
				    total_tokens bigint not null,
				    input_characters bigint not null,
				    output_characters bigint not null,
				    audio_input_seconds decimal(16,3) not null,
				    audio_output_seconds decimal(16,3) not null,
				    usage_source varchar(16) not null,
				    status varchar(16) not null,
				    error_code varchar(128),
				    retryable boolean not null,
				    fallback_from_model_id varchar(128),
				    estimated_cost decimal(20,8) not null,
				    price_currency varchar(8) not null
				)
				""");
		jdbc.update("insert into users values (?, ?)",
				java.util.UUID.fromString("10000000-0000-0000-0000-000000000001"), "learner@example.com");
		jdbc.update("""
				insert into ai_model_invocations values
				('20000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', 1,
				 '10000000-0000-0000-0000-000000000001', 'session-1', 'dialogue_report', 'default', 'LLM',
				 'qwen', 'qwen3.5-plus', 'vendor-1', '2026-08-18T09:00:00Z', '2026-08-18T09:00:00.230Z',
				 230, null, 80, 20, 100, 320, 80, 0, 0, 'PROVIDER', 'FAILED', 'TIMEOUT', true, null, 0.01000000, 'CNY'),
				('20000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', 2,
				 '10000000-0000-0000-0000-000000000001', 'session-1', 'dialogue_report', 'default', 'LLM',
				 'deepseek', 'deepseek-v4-flash', 'vendor-2', '2026-08-18T09:00:00.250Z', '2026-08-18T09:00:00.790Z',
				 540, 120, 40, 10, 50, 160, 40, 0, 0, 'PROVIDER', 'SUCCEEDED', null, false,
				 'qwen3.5-plus', 0.02000000, 'CNY')
				""");
		return jdbc;
	}
}
