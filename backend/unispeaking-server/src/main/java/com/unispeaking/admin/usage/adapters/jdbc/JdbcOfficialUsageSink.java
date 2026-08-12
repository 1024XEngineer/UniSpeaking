package com.unispeaking.admin.usage.adapters.jdbc;

import com.unispeaking.admin.usage.domain.OfficialUsageRecord;
import com.unispeaking.admin.usage.ports.OfficialUsageSink;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Stores official Alibaba inference usage in the canonical backend database. */
@Component
@ConditionalOnProperty(
        name = "unispeaking.integrations.freetalk.enabled",
        havingValue = "false",
        matchIfMissing = true)
public final class JdbcOfficialUsageSink implements OfficialUsageSink {
    private final JdbcTemplate jdbc;

    public JdbcOfficialUsageSink(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ImportResult importRecords(List<OfficialUsageRecord> records) {
        int imported = 0;
        int duplicates = 0;
        int matched = 0;
        int unmatched = 0;
        for (OfficialUsageRecord record : records) {
            if (exists(record.requestId())) {
                duplicates++;
                continue;
            }
            try {
                var usage = record.usage();
                jdbc.update(
                        "insert into official_usage_records "
                                + "(request_id, task_uuid, started_at_epoch_ms, duration_ms, status_code, model, "
                                + "workspace_id, apikey_id, protocol, requests, total_tokens, input_tokens, "
                                + "output_tokens, input_text_tokens, input_audio_tokens, output_text_tokens, "
                                + "output_audio_tokens, imported_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        record.requestId(), record.taskUuid(), record.startedAtEpochMs(), record.durationMs(),
                        record.statusCode(), record.model(), record.workspaceId(), record.apiKeyId(), record.protocol(),
                        usage.responseCount(), usage.totalTokens(), usage.inputTokens(), usage.outputTokens(),
                        usage.inputTextTokens(), usage.inputAudioTokens(), usage.outputTextTokens(),
                        usage.outputAudioTokens(), OffsetDateTime.now(ZoneOffset.UTC));
                imported++;
                if (isBound(record.taskUuid())) matched++;
                else unmatched++;
            } catch (DuplicateKeyException exception) {
                duplicates++;
            }
        }
        return new ImportResult(imported, duplicates, matched, unmatched);
    }

    private boolean exists(String requestId) {
        Long count = jdbc.queryForObject(
                "select count(*) from official_usage_records where request_id = ?",
                Long.class,
                requestId);
        return count != null && count > 0;
    }

    private boolean isBound(String taskUuid) {
        Long count = jdbc.queryForObject(
                "select count(*) from practice_session where provider_session_id = ?",
                Long.class,
                taskUuid);
        return count != null && count > 0;
    }
}
