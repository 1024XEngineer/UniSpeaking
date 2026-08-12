package com.unispeaking.admin.usage.adapters.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.OfficialUsageRecord;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcOfficialUsageSinkTest {
    @Test
    void importsOfficialUsageIdempotentlyAndMatchesPersistedProviderSession() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:official-usage-sink;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table practice_session (session_id varchar(64) primary key, user_id uuid not null, "
                + "provider_session_id varchar(128))");
        jdbc.execute("create table official_usage_records (request_id varchar(128) primary key, "
                + "task_uuid varchar(128) not null, started_at_epoch_ms bigint not null, duration_ms bigint not null, "
                + "status_code varchar(64) not null, model varchar(128) not null, workspace_id varchar(128) not null, "
                + "apikey_id varchar(128) not null, protocol varchar(16) not null, requests bigint not null, "
                + "total_tokens bigint not null, input_tokens bigint not null, output_tokens bigint not null, "
                + "input_text_tokens bigint not null, input_audio_tokens bigint not null, "
                + "output_text_tokens bigint not null, output_audio_tokens bigint not null, "
                + "imported_at timestamp with time zone not null)");
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into practice_session (session_id, user_id, provider_session_id) values (?, ?, ?)",
                "session-local", userId, "sess-provider");
        var record = new OfficialUsageRecord(
                "request-1", "sess-provider", 1000, 2500, "200", "qwen-realtime",
                "workspace", "apikey", "webrtc", new ModelUsage(1, 100, 70, 30, 20, 50, 10, 20));
        var sink = new JdbcOfficialUsageSink(jdbc);

        var first = sink.importRecords(List.of(record));
        var repeated = sink.importRecords(List.of(record));

        assertThat(first.imported()).isEqualTo(1);
        assertThat(first.matched()).isEqualTo(1);
        assertThat(first.unmatched()).isZero();
        assertThat(repeated.imported()).isZero();
        assertThat(repeated.duplicates()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select total_tokens from official_usage_records where request_id = 'request-1'", Long.class))
                .isEqualTo(100L);
    }
}
