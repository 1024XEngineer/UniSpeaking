package com.unispeaking.admin.usage.adapters.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unispeaking.admin.usage.application.UsageSourceUnavailableException;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcUsageDataSourceTest {
    @Test
    void readsRegisteredEmailUsersWithDailyQuota() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:admin-daily;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table \"user\" (id uuid primary key, username varchar(320) not null, "
                + "password_hash varchar(1000) not null, nickname varchar(32), role varchar(16), status varchar(16), "
                + "auth_version bigint, last_login_at timestamp with time zone, created_at timestamp with time zone not null, "
                + "updated_at timestamp with time zone not null)");
        jdbc.execute("create table user_entitlements (user_id uuid not null, quota_date date not null, "
                + "plan_code varchar(64), plan_name varchar(128), quota_seconds numeric(12,3), "
                + "used_seconds numeric(12,3), status varchar(32), updated_at timestamp with time zone)");
        jdbc.execute("create table practice_session (session_id varchar(64) primary key, user_id uuid not null, "
                + "status varchar(16) not null, started_at timestamp with time zone not null, "
                + "ended_at timestamp with time zone, provider_session_id varchar(128))");
        UUID userId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        jdbc.update("insert into \"user\" (id, username, password_hash, created_at, updated_at) "
                        + "values (?, ?, 'hash', current_timestamp, current_timestamp)",
                userId, "learner@example.com");
        jdbc.update("insert into user_entitlements "
                        + "(user_id, quota_date, plan_code, plan_name, quota_seconds, used_seconds, status, updated_at) "
                        + "values (?, current_date, 'free', 'Free', 600, 125.5, 'active', current_timestamp)",
                userId);
        var users = new JdbcUsageDataSource(jdbc).loadSnapshot().users();

        assertThat(users).hasSize(1);
        assertThat(users.getFirst().displayName()).isEqualTo("learner@example.com");
        assertThat(users.getFirst().remainingSeconds()).isEqualTo(474.5);
        assertThat(users.getFirst().estimatedCostCny()).isEqualTo("0");
    }

    @Test
    void reportsMissingSessionStorageAsAnUnavailableUsageSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:admin-storage-failure;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table \"user\" (id uuid primary key, username varchar(320) not null, "
                + "password_hash varchar(1000) not null, created_at timestamp with time zone not null)");
        jdbc.execute("create table user_entitlements (user_id uuid not null, quota_date date not null, "
                + "plan_code varchar(64), plan_name varchar(128), quota_seconds numeric(12,3), "
                + "used_seconds numeric(12,3), status varchar(32))");
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into \"user\" (id, username, password_hash, created_at) "
                        + "values (?, 'broken@example.com', 'hash', current_timestamp)", userId);
        jdbc.update("insert into user_entitlements values (?, current_date, 'free', 'Free', 600, 0, 'active')", userId);

        assertThatThrownBy(() -> new JdbcUsageDataSource(jdbc).loadSnapshot())
                .isInstanceOf(UsageSourceUnavailableException.class)
                .hasMessageContaining("PostgreSQL");
    }

    @Test
    void onlyCountsSessionsThatBelongToTheCurrentQuotaDate() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:admin-current-day;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table \"user\" (id uuid primary key, username varchar(320) not null, "
                + "password_hash varchar(1000) not null, nickname varchar(32), role varchar(16), status varchar(16), "
                + "auth_version bigint, last_login_at timestamp with time zone, created_at timestamp with time zone not null, "
                + "updated_at timestamp with time zone not null)");
        jdbc.execute("create table user_entitlements (user_id uuid not null, quota_date date not null, "
                + "plan_code varchar(64), plan_name varchar(128), quota_seconds numeric(12,3), "
                + "used_seconds numeric(12,3), status varchar(32), updated_at timestamp with time zone)");
        jdbc.execute("create table practice_session (session_id varchar(64) primary key, user_id uuid not null, "
                + "status varchar(16) not null, started_at timestamp with time zone not null, "
                + "ended_at timestamp with time zone, provider_session_id varchar(128))");
        jdbc.execute("create table official_usage_records (request_id varchar(128) primary key, "
                + "task_uuid varchar(128) not null, started_at_epoch_ms bigint not null, duration_ms bigint not null, "
                + "status_code varchar(64) not null, model varchar(128) not null, workspace_id varchar(128) not null, "
                + "apikey_id varchar(128) not null, protocol varchar(16) not null, requests bigint not null, "
                + "total_tokens bigint not null, input_tokens bigint not null, output_tokens bigint not null, "
                + "input_text_tokens bigint not null, input_audio_tokens bigint not null, "
                + "output_text_tokens bigint not null, output_audio_tokens bigint not null, "
                + "imported_at timestamp with time zone not null)");
        UUID userId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        jdbc.update("insert into \"user\" (id, username, password_hash, created_at, updated_at) "
                        + "values (?, ?, 'hash', current_timestamp, current_timestamp)",
                userId, "daily@example.com");
        jdbc.update("insert into user_entitlements "
                        + "(user_id, quota_date, plan_code, plan_name, quota_seconds, used_seconds, status, updated_at) "
                        + "values (?, current_date, 'free', 'Free', 600, 0, 'active', current_timestamp)",
                userId);
        OffsetDateTime today = OffsetDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime historical = today.minusDays(2);
        jdbc.update("insert into practice_session (session_id, user_id, status, started_at, ended_at, provider_session_id) "
                        + "values ('session-today', ?, 'COMPLETED', ?, ?, 'sess-provider-today')",
                userId, today, today.plusSeconds(90));
        jdbc.update("insert into practice_session (session_id, user_id, status, started_at, ended_at) "
                        + "values ('session-history', ?, 'COMPLETED', ?, ?)",
                userId, historical, historical.plusSeconds(240));
        jdbc.update("insert into official_usage_records "
                        + "(request_id, task_uuid, started_at_epoch_ms, duration_ms, status_code, model, workspace_id, "
                        + "apikey_id, protocol, requests, total_tokens, input_tokens, output_tokens, input_text_tokens, "
                        + "input_audio_tokens, output_text_tokens, output_audio_tokens, imported_at) values "
                        + "('request-today', 'sess-provider-today', 1, 90000, '200', 'qwen-realtime', 'workspace', "
                        + "'apikey', 'webrtc', 1, 120, 80, 40, 20, 60, 10, 30, current_timestamp)");

        var user = new JdbcUsageDataSource(jdbc).loadSnapshot().users().getFirst();

        assertThat(user.sessions()).extracting(session -> session.sessionId())
                .containsExactly("session-today");
        assertThat(user.sessions().getFirst().taskUuid()).isEqualTo("sess-provider-today");
        assertThat(user.sessions().getFirst().providerRequestId()).isEqualTo("request-today");
        assertThat(user.sessions().getFirst().officialUsage().totalTokens()).isEqualTo(120);
        assertThat(user.sessions().getFirst().reconciliationStatus()).isEqualTo("MATCHED");
        assertThat(user.sessionCount()).isEqualTo(1);
        assertThat(user.usedSeconds()).isEqualTo(90);
    }

    @Test
    void preservesPlanAndStatusButResetsUsageWhenTheStoredQuotaDateIsStale() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:admin-stale-entitlement;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table \"user\" (id uuid primary key, username varchar(320) not null, "
                + "password_hash varchar(1000) not null, nickname varchar(32), role varchar(16), status varchar(16), "
                + "auth_version bigint, last_login_at timestamp with time zone, created_at timestamp with time zone not null, "
                + "updated_at timestamp with time zone not null)");
        jdbc.execute("create table user_entitlements (user_id uuid not null, quota_date date not null, "
                + "plan_code varchar(64), plan_name varchar(128), quota_seconds numeric(12,3), "
                + "used_seconds numeric(12,3), status varchar(32), updated_at timestamp with time zone)");
        jdbc.execute("create table practice_session (session_id varchar(64) primary key, user_id uuid not null, "
                + "status varchar(16) not null, started_at timestamp with time zone not null, "
                + "ended_at timestamp with time zone, provider_session_id varchar(128))");
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into \"user\" (id, username, password_hash, created_at, updated_at) "
                        + "values (?, ?, 'hash', current_timestamp, current_timestamp)",
                userId, "stale@example.com");
        jdbc.update("insert into user_entitlements "
                        + "(user_id, quota_date, plan_code, plan_name, quota_seconds, used_seconds, status, updated_at) "
                        + "values (?, dateadd('DAY', -1, current_date), 'pro', 'Pro', 3600, 2400, 'suspended', current_timestamp)",
                userId);
        OffsetDateTime today = OffsetDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);
        jdbc.update("insert into practice_session (session_id, user_id, status, started_at, ended_at) "
                        + "values ('session-current-day', ?, 'COMPLETED', ?, ?)",
                userId, today, today.plusSeconds(90));

        var user = new JdbcUsageDataSource(jdbc).loadSnapshot().users().getFirst();

        assertThat(user.planCode()).isEqualTo("pro");
        assertThat(user.planName()).isEqualTo("Pro");
        assertThat(user.status()).isEqualTo("suspended");
        assertThat(user.quotaSeconds()).isEqualTo(3600);
        assertThat(user.quotaDate()).isEqualTo(java.time.LocalDate.now().toString());
        assertThat(user.usedSeconds()).isEqualTo(90);
        assertThat(user.remainingSeconds()).isEqualTo(3510);
        assertThat(user.sessionCount()).isEqualTo(1);
    }
}
