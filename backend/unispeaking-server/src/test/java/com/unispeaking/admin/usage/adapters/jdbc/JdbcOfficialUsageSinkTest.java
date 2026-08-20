package com.unispeaking.admin.usage.adapters.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.OfficialUsageRecord;
import java.lang.reflect.Modifier;
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
        createTables(jdbc);
        jdbc.update("insert into ai_models values (?, ?, ?, ?, ?, ?)",
                "qwen-realtime", "TOKENS", 0, 0, 0, 0);
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

    @Test
    void reconcilesLlmTtsAndRealtimeInvocationsWithOfficialUsage() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:official-usage-reconcile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createTables(jdbc);
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into practice_session (session_id, user_id, provider_session_id) values (?, ?, ?)",
                "realtime-local", userId, "sess-provider");
        jdbc.update("insert into ai_models values (?, ?, ?, ?, ?, ?)",
                "qwen3.5-plus", "TOKENS", 8, 48, 0, 0);
        jdbc.update("insert into ai_models values (?, ?, ?, ?, ?, ?)",
                "qwen3-tts-flash", "CHARACTERS", 0, 0, 800, 0);
        jdbc.update("insert into ai_models values (?, ?, ?, ?, ?, ?)",
                "qwen3.5-omni-flash-realtime", "MIXED", 3.3, 20, 0, 0);
        insertInvocation(jdbc, "llm-invocation", null, "llm", "qwen3.5-plus", "request-llm",
                pricing("TOKENS", 0.8, 4.8, 0));
        insertInvocation(jdbc, "tts-invocation", null, "tts", "qwen3-tts-flash", "request-tts",
                pricing("CHARACTERS", 0, 0, 80));
        insertInvocation(jdbc, "realtime-invocation", "realtime-local", "realtime_session",
                "qwen3.5-omni-flash-realtime", "handshake-request",
                pricing("TOKENS", 3.3, 20, 0));
        var sink = new JdbcOfficialUsageSink(jdbc);

        var result = sink.importRecords(List.of(
                new OfficialUsageRecord("request-llm", null, 1000, 3566, "200", "qwen3.5-plus",
                        "workspace", "apikey", "http", new ModelUsage(1, 1147, 970, 177, 970, 0, 177, 0), 0),
                new OfficialUsageRecord("request-tts", null, 1001, 889, "200", "qwen3-tts-flash",
                        "workspace", "apikey", "http", new ModelUsage(1, 0, 0, 0, 0, 0, 0, 0), 51),
                new OfficialUsageRecord("request-realtime", "sess-provider", 1002, 35816, "200",
                        "qwen3.5-omni-flash-realtime", "workspace", "apikey", "ws",
                        new ModelUsage(1, 2879, 2707, 172, 2679, 28, 46, 126), 0)));

        assertThat(result.matched()).isEqualTo(3);
        assertThat(jdbc.queryForMap("select provider_request_id, total_tokens, usage_source, estimated_cost "
                + "from ai_model_invocations where invocation_id = 'llm-invocation'"))
                .containsEntry("PROVIDER_REQUEST_ID", "request-llm")
                .containsEntry("TOTAL_TOKENS", 1147L)
                .containsEntry("USAGE_SOURCE", "OFFICIAL");
        assertThat(jdbc.queryForMap("select input_characters, usage_source, estimated_cost "
                + "from ai_model_invocations where invocation_id = 'tts-invocation'"))
                .containsEntry("INPUT_CHARACTERS", 51L)
                .containsEntry("USAGE_SOURCE", "OFFICIAL");
        assertThat(jdbc.queryForMap("select provider_request_id, total_tokens, usage_source "
                + "from ai_model_invocations where invocation_id = 'realtime-invocation'"))
                .containsEntry("PROVIDER_REQUEST_ID", "request-realtime")
                .containsEntry("TOTAL_TOKENS", 2879L)
                .containsEntry("USAGE_SOURCE", "OFFICIAL");
        assertThat(jdbc.queryForObject("select estimated_cost from ai_model_invocations where invocation_id='llm-invocation'",
                java.math.BigDecimal.class)).isEqualByComparingTo("0.00162560");
        assertThat(jdbc.queryForObject("select estimated_cost from ai_model_invocations where invocation_id='tts-invocation'",
                java.math.BigDecimal.class)).isEqualByComparingTo("0.00408000");
    }

    @Test
    void reconcilesOfficialCharactersForAFailedButBilledTtsAttempt() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:official-usage-failed-tts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createTables(jdbc);
        jdbc.update("insert into ai_models values (?, ?, ?, ?, ?, ?)",
                "qwen3-tts-flash", "CHARACTERS", 0, 0, 800, 0);
        insertInvocation(jdbc, "failed-tts", null, "tts", "qwen3-tts-flash", "billed-request",
                pricing("CHARACTERS", 0, 0, 80));
        jdbc.update("update ai_model_invocations set status = 'FAILED' where invocation_id = 'failed-tts'");
        var sink = new JdbcOfficialUsageSink(jdbc);

        var result = sink.importRecords(List.of(new OfficialUsageRecord(
                "billed-request", null, 1000, 500, "200", "qwen3-tts-flash",
                "workspace", "apikey", "http", new ModelUsage(1, 0, 0, 0, 0, 0, 0, 0), 24)));

        assertThat(result.matched()).isEqualTo(1);
        assertThat(result.unmatched()).isZero();
        assertThat(jdbc.queryForMap("select status, input_characters, usage_source, estimated_cost "
                + "from ai_model_invocations where invocation_id = 'failed-tts'"))
                .containsEntry("STATUS", "FAILED")
                .containsEntry("INPUT_CHARACTERS", 24L)
                .containsEntry("USAGE_SOURCE", "OFFICIAL");
        assertThat(jdbc.queryForObject("select estimated_cost from ai_model_invocations "
                + "where invocation_id = 'failed-tts'", java.math.BigDecimal.class))
                .isEqualByComparingTo("0.00192000");
    }

    @Test
    void aggregatesAllOfficialRealtimeRecordsForTheSameProviderSession() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:official-usage-realtime-aggregate;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createTables(jdbc);
        UUID userId = UUID.randomUUID();
        jdbc.update("insert into practice_session (session_id, user_id, provider_session_id) values (?, ?, ?)",
                "realtime-local", userId, "sess-provider");
        jdbc.update("insert into ai_models values (?, ?, ?, ?, ?, ?)",
                "qwen3.5-omni-flash-realtime", "TOKENS", 33, 200, 0, 0);
        insertInvocation(jdbc, "realtime-invocation", "realtime-local", "realtime_session",
                "qwen3.5-omni-flash-realtime", "handshake-request",
                pricing("TOKENS", 3.3, 20, 0));
        var sink = new JdbcOfficialUsageSink(jdbc);

        sink.importRecords(List.of(
                new OfficialUsageRecord("request-b", "sess-provider", 2000, 200, "200",
                        "qwen3.5-omni-flash-realtime", "workspace", "apikey", "ws",
                        new ModelUsage(1, 180, 120, 60, 100, 20, 40, 20), 0),
                new OfficialUsageRecord("request-a", "sess-provider", 1000, 100, "200",
                        "qwen3.5-omni-flash-realtime", "workspace", "apikey", "ws",
                        new ModelUsage(1, 120, 80, 40, 70, 10, 30, 10), 0)));

        assertThat(jdbc.queryForMap("select provider_request_id, input_tokens, output_tokens, total_tokens, "
                + "estimated_cost from ai_model_invocations where invocation_id = 'realtime-invocation'"))
                .containsEntry("PROVIDER_REQUEST_ID", "request-b")
                .containsEntry("INPUT_TOKENS", 200L)
                .containsEntry("OUTPUT_TOKENS", 100L)
                .containsEntry("TOTAL_TOKENS", 300L);
        assertThat(jdbc.queryForObject("select estimated_cost from ai_model_invocations "
                + "where invocation_id = 'realtime-invocation'", java.math.BigDecimal.class))
                .isEqualByComparingTo("0.00266000");
    }

    @Test
    void keepsFallbackPricingStableAfterFirstOfficialReconciliation() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:official-usage-legacy-price;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        createTables(jdbc);
        jdbc.update("insert into ai_models values (?, ?, ?, ?, ?, ?)",
                "legacy-model", "TOKENS", 0.8, 4.8, 0, 0);
        insertInvocation(jdbc, "legacy-invocation", null, "llm", "legacy-model", "legacy-request", null);
        var sink = new JdbcOfficialUsageSink(jdbc);
        var record = new OfficialUsageRecord("legacy-request", null, 1000, 100, "200", "legacy-model",
                "workspace", "apikey", "http", new ModelUsage(1, 100, 80, 20, 80, 0, 20, 0), 0);

        sink.importRecords(List.of(record));
        var firstCost = jdbc.queryForObject("select estimated_cost from ai_model_invocations "
                + "where invocation_id = 'legacy-invocation'", java.math.BigDecimal.class);
        jdbc.update("update ai_model_invocations set pricing_snapshot = ? where invocation_id = 'legacy-invocation'",
                "{\"billing_unit\":\"TOKENS\",\"input_price_per_million\":\"invalid\"}");
        jdbc.update("update ai_models set input_price_per_million = 8, output_price_per_million = 48 "
                + "where model_id = 'legacy-model'");
        sink.importRecords(List.of(record));

        assertThat(firstCost).isEqualByComparingTo("0.00016000");
        assertThat(jdbc.queryForObject("select estimated_cost from ai_model_invocations "
                + "where invocation_id = 'legacy-invocation'", java.math.BigDecimal.class))
                .isEqualByComparingTo(firstCost);
    }

    @Test
    void serializesOverlappingOfficialUsageImports() throws NoSuchMethodException {
        assertThat(Modifier.isSynchronized(JdbcOfficialUsageSink.class
                .getMethod("importRecords", List.class)
                .getModifiers())).isTrue();
    }

    private static void createTables(JdbcTemplate jdbc) {
        jdbc.execute("create table practice_session (session_id varchar(64) primary key, user_id uuid not null, provider_session_id varchar(128))");
        jdbc.execute("create table official_usage_records (request_id varchar(128) primary key, task_uuid varchar(128), "
                + "started_at_epoch_ms bigint not null, duration_ms bigint not null, status_code varchar(64) not null, "
                + "model varchar(128) not null, workspace_id varchar(128) not null, apikey_id varchar(128) not null, "
                + "protocol varchar(16) not null, requests bigint not null, total_tokens bigint not null, "
                + "input_tokens bigint not null, output_tokens bigint not null, input_text_tokens bigint not null, "
                + "input_audio_tokens bigint not null, output_text_tokens bigint not null, output_audio_tokens bigint not null, "
                + "characters bigint not null, imported_at timestamp with time zone not null)");
        jdbc.execute("create table ai_models (model_id varchar(128) primary key, billing_unit varchar(32), "
                + "input_price_per_million numeric(20,8), output_price_per_million numeric(20,8), "
                + "character_price_per_million numeric(20,8), request_price_per_call numeric(20,8))");
        jdbc.execute("create table ai_model_invocations (invocation_id varchar(64) primary key, session_id varchar(64), "
                + "business_scene varchar(64), model_id varchar(128), provider_request_id varchar(256), "
                + "input_tokens bigint default 0, output_tokens bigint default 0, total_tokens bigint default 0, "
                + "input_characters bigint default 0, output_characters bigint default 0, usage_source varchar(16), "
                + "status varchar(16), estimated_cost numeric(20,8), pricing_snapshot varchar(2000))");
    }

    private static void insertInvocation(JdbcTemplate jdbc, String id, String sessionId, String scene,
            String model, String requestId, String pricingSnapshot) {
        jdbc.update("insert into ai_model_invocations (invocation_id, session_id, business_scene, model_id, "
                        + "provider_request_id, usage_source, status, estimated_cost, pricing_snapshot) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, sessionId, scene, model, requestId, "ESTIMATED", "SUCCEEDED", 0, pricingSnapshot);
    }

    private static String pricing(String billingUnit, double input, double output, double characters) {
        return "{\"billing_unit\":\"" + billingUnit + "\","
                + "\"input_price_per_million\":" + input + ","
                + "\"output_price_per_million\":" + output + ","
                + "\"character_price_per_million\":" + characters + ","
                + "\"request_price_per_call\":0}";
    }
}
