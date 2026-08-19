package com.unispeaking.admin.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.unispeaking.admin.usage.adapters.aliyun.AliyunInferenceLogParser;
import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.ProviderStatus;
import com.unispeaking.admin.usage.domain.UsageSession;
import com.unispeaking.admin.usage.domain.UsageSnapshot;
import com.unispeaking.admin.usage.domain.UsageUser;
import com.unispeaking.admin.usage.ports.OfficialUsageSink;
import com.unispeaking.admin.usage.ports.UsageDataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OfficialUsageSyncServiceTest {
    @Test
    void importsOnlyRecordsBoundToLocalSessionsAndDeduplicatesBatch() {
        var accepted = new ArrayList<String>();
        var source = new StubOfficialUsageLogSource(List.of(
                json("request-01", "sess_local_01", "ws-local", "qwen3.5-omni-flash-realtime"),
                json("request-01", "sess_local_01", "ws-local", "qwen3.5-omni-flash-realtime"),
                json("request-02", "sess_other", "ws-local", "qwen3.5-omni-flash-realtime"),
                json("request-03", "sess_local_01", "ws-other", "qwen3.5-omni-flash-realtime")));
        OfficialUsageSink sink = records -> {
            records.forEach(record -> accepted.add(record.requestId()));
            return new OfficialUsageSink.ImportResult(records.size(), 0, records.size(), 0);
        };
        var service = new OfficialUsageSyncService(
                source,
                sink,
                snapshot("sess_local_01"),
                new AliyunInferenceLogParser(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-07-20T08:10:00Z"), ZoneOffset.UTC),
                "ws-local",
                "qwen3.5-omni-flash-realtime",
                600);

        var result = service.syncNow();

        assertThat(accepted).containsExactly("request-01");
        assertThat(result.scanned()).isEqualTo(4);
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.duplicate()).isEqualTo(1);
        assertThat(result.unbound()).isEqualTo(1);
        assertThat(result.rejectedContext()).isEqualTo(1);
        assertThat(source.from).isEqualTo(Instant.parse("2026-07-20T08:00:00Z"));
        assertThat(source.to).isEqualTo(Instant.parse("2026-07-20T08:10:01Z"));
    }

    @Test
    void importsRealtimeAuditLogWhenZeroValuedAudioDetailIsOmitted() {
        var accepted = new ArrayList<com.unispeaking.admin.usage.domain.OfficialUsageRecord>();
        var source = new StubOfficialUsageLogSource(List.of("""
                {
                  "start_unix_timestamp":"1787104110590",
                  "status_code":"200",
                  "usage":{
                    "input_tokens_details":{"text_tokens":736},
                    "total_tokens":793,
                    "output_tokens":57,
                    "input_tokens":736,
                    "output_tokens_details":{"audio_tokens":41,"text_tokens":16}
                  },
                  "extras":{"protocol":"ws"},
                  "apikey_id":"6126227",
                  "duration":"51014",
                  "workspace_id":"ws-67zfnonsdn4x96ia",
                  "model":"qwen3.5-omni-flash-realtime",
                  "task_uuid":"sess_9tCc7hYPDCZPM7jmiPI3t",
                  "request_id":"cff4d5fd-67e0-926c-bfea-1f2750db8c72"
                }
                """));
        OfficialUsageSink sink = records -> {
            accepted.addAll(records);
            return new OfficialUsageSink.ImportResult(records.size(), 0, records.size(), 0);
        };
        var service = new OfficialUsageSyncService(
                source,
                sink,
                snapshot("sess_9tCc7hYPDCZPM7jmiPI3t"),
                new AliyunInferenceLogParser(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-08-19T01:50:00Z"), ZoneOffset.UTC),
                "ws-67zfnonsdn4x96ia",
                "qwen3.5-omni-flash-realtime",
                600);

        var result = service.syncNow();

        assertThat(result.accepted()).isEqualTo(1);
        assertThat(result.rejectedSchema()).isZero();
        assertThat(result.matched()).isEqualTo(1);
        assertThat(accepted).singleElement().satisfies(record -> {
            assertThat(record.requestId()).isEqualTo("cff4d5fd-67e0-926c-bfea-1f2750db8c72");
            assertThat(record.taskUuid()).isEqualTo("sess_9tCc7hYPDCZPM7jmiPI3t");
            assertThat(record.usage().inputAudioTokens()).isZero();
            assertThat(record.usage().totalTokens()).isEqualTo(793);
        });
    }

    @Test
    void acceptsSuccessfulHttpLlmAndTtsLogsButRejectsRateLimitedTts() {
        var accepted = new ArrayList<com.unispeaking.admin.usage.domain.OfficialUsageRecord>();
        var source = new StubOfficialUsageLogSource(List.of(
                httpJson("request-llm", "qwen3.5-plus", "200",
                        "{\"input_tokens\":970,\"output_tokens\":177,\"total_tokens\":1147,"
                                + "\"input_tokens_details\":{\"text_tokens\":970},"
                                + "\"output_tokens_details\":{\"text_tokens\":177}}"),
                httpJson("request-tts", "qwen3-tts-flash", "200", "{\"characters\":51}"),
                httpJson("request-rate-limit", "qwen3-tts-flash", "429", "{}")));
        OfficialUsageSink sink = records -> {
            accepted.addAll(records);
            return new OfficialUsageSink.ImportResult(records.size(), 0, records.size(), 0);
        };
        var service = new OfficialUsageSyncService(
                source,
                sink,
                snapshot("sess_local_01", "request-llm", "request-tts"),
                new AliyunInferenceLogParser(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-08-19T02:40:00Z"), ZoneOffset.UTC),
                "ws-67zfnonsdn4x96ia",
                "qwen3.5-omni-flash-realtime",
                600);

        var result = service.syncNow();

        assertThat(accepted).extracting(record -> record.requestId())
                .containsExactly("request-llm", "request-tts");
        assertThat(result.rejectedContext()).isEqualTo(1);
    }

    private static UsageDataSource snapshot(String taskUuid, String... requestIds) {
        ModelUsage empty = new ModelUsage(0, 0, 0, 0, 0, 0, 0, 0);
        UsageSession session = new UsageSession(
                "local-01", "user-01", "free", "ended", 50, 130,
                "key-id", "fingerprint", 1784535000L, taskUuid, null,
                empty, empty, null, null, "pending", "PENDING", List.of(), "user_end");
        UsageUser user = new UsageUser(
                "user-01", "User 01", "free", "Free", "active", "2026-07-20",
                180, 50, 0, 50, 130, 1784563200d, null, 1,
                List.of(session), empty, empty, null, Map.of("PENDING", 1));
        return new UsageDataSource() {
            @Override
            public UsageSnapshot loadSnapshot() {
                return new UsageSnapshot(
                        List.of(user), null,
                        new ProviderStatus(null, Map.of(), List.of(), 0, "/tmp/inbox", 3d, true));
            }

            @Override
            public java.util.Set<String> localProviderRequestIds() {
                return java.util.Set.of(requestIds);
            }
        };
    }

    private static String json(String requestId, String taskUuid, String workspaceId, String model) {
        return """
                {
                  "start_unix_timestamp":"1784534676105",
                  "status_code":"200",
                  "usage":{
                    "input_tokens_details":{"text_tokens":12840,"audio_tokens":98},
                    "total_tokens":13289,
                    "output_tokens":351,
                    "input_tokens":12938,
                    "output_tokens_details":{"audio_tokens":266,"text_tokens":85}
                  },
                  "extras":{"protocol":"ws"},
                  "apikey_id":"6124876",
                  "duration":"50962",
                  "workspace_id":"%s",
                  "model":"%s",
                  "task_uuid":"%s",
                  "request_id":"%s"
                }
                """.formatted(workspaceId, model, taskUuid, requestId);
    }

    private static String httpJson(String requestId, String model, String status, String usage) {
        return """
                {
                  "start_unix_timestamp":"1787106742708",
                  "status_code":"%s",
                  "usage":%s,
                  "extras":{"protocol":"HTTP"},
                  "apikey_id":"6126227",
                  "duration":"889",
                  "workspace_id":"ws-67zfnonsdn4x96ia",
                  "model":"%s",
                  "request_id":"%s"
                }
                """.formatted(status, usage, model, requestId);
    }

    private static final class StubOfficialUsageLogSource implements com.unispeaking.admin.usage.ports.OfficialUsageLogSource {
        private final List<String> logs;
        private Instant from;
        private Instant to;

        private StubOfficialUsageLogSource(List<String> logs) {
            this.logs = logs;
        }

        @Override
        public List<String> loadLogs(Instant from, Instant to) {
            this.from = from;
            this.to = to;
            return logs;
        }
    }
}
