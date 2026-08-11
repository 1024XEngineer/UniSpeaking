package com.unispeaking.admin.usage.application;

import com.unispeaking.admin.usage.adapters.aliyun.AliyunInferenceLogParser;
import com.unispeaking.admin.usage.adapters.aliyun.OfficialUsageSchemaException;
import com.unispeaking.admin.usage.domain.OfficialUsageRecord;
import com.unispeaking.admin.usage.ports.OfficialUsageLogSource;
import com.unispeaking.admin.usage.ports.OfficialUsageSink;
import com.unispeaking.admin.usage.ports.UsageDataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Service
@ConditionalOnBean(OfficialUsageSink.class)
public final class OfficialUsageSyncService {
    private final OfficialUsageLogSource logSource;
    private final OfficialUsageSink sink;
    private final UsageDataSource usageDataSource;
    private final AliyunInferenceLogParser parser;
    private final Clock clock;
    private final String expectedWorkspaceId;
    private final String expectedModel;
    private final int lookbackSeconds;

    @Autowired
    public OfficialUsageSyncService(
            OfficialUsageLogSource logSource,
            OfficialUsageSink sink,
            UsageDataSource usageDataSource,
            AliyunInferenceLogParser parser,
            @Value("${unispeaking.integrations.aliyun.workspace-id:}") String expectedWorkspaceId,
            @Value("${unispeaking.integrations.aliyun.model:qwen3.5-omni-flash-realtime}") String expectedModel,
            @Value("${unispeaking.integrations.aliyun.sync-lookback-seconds:600}") int lookbackSeconds) {
        this(logSource, sink, usageDataSource, parser, Clock.systemUTC(), expectedWorkspaceId, expectedModel, lookbackSeconds);
    }

    public OfficialUsageSyncService(
            OfficialUsageLogSource logSource,
            OfficialUsageSink sink,
            UsageDataSource usageDataSource,
            AliyunInferenceLogParser parser,
            Clock clock,
            String expectedWorkspaceId,
            String expectedModel,
            int lookbackSeconds) {
        this.logSource = logSource;
        this.sink = sink;
        this.usageDataSource = usageDataSource;
        this.parser = parser;
        this.clock = clock;
        this.expectedWorkspaceId = expectedWorkspaceId;
        this.expectedModel = expectedModel;
        this.lookbackSeconds = lookbackSeconds;
    }

    public SyncResult syncNow() {
        Instant now = clock.instant();
        Instant from = now.minusSeconds(lookbackSeconds);
        Instant to = now.plusSeconds(1);
        List<String> rawLogs = logSource.loadLogs(from, to);
        Set<String> localTaskUuids = localTaskUuids();
        var unique = new LinkedHashMap<String, OfficialUsageRecord>();
        int rejectedSchema = 0;
        int rejectedContext = 0;
        int unbound = 0;
        int duplicate = 0;

        for (String raw : rawLogs) {
            OfficialUsageRecord record;
            try {
                record = parser.parse(raw);
            } catch (OfficialUsageSchemaException exception) {
                rejectedSchema++;
                continue;
            }
            if (!matchesExpectedContext(record)) {
                rejectedContext++;
                continue;
            }
            if (!localTaskUuids.contains(record.taskUuid())) {
                unbound++;
                continue;
            }
            if (unique.putIfAbsent(record.requestId(), record) != null) {
                duplicate++;
            }
        }

        List<OfficialUsageRecord> accepted = new ArrayList<>(unique.values());
        OfficialUsageSink.ImportResult imported = accepted.isEmpty()
                ? new OfficialUsageSink.ImportResult(0, 0, 0, 0)
                : sink.importRecords(accepted);
        return new SyncResult(
                rawLogs.size(), accepted.size(), duplicate, unbound, rejectedContext, rejectedSchema,
                imported.imported(), imported.duplicates(), imported.matched(), imported.unmatched(), now);
    }

    private Set<String> localTaskUuids() {
        var ids = new HashSet<String>();
        usageDataSource.loadSnapshot().users().forEach(user -> user.sessions().forEach(session -> {
            if (session.taskUuid() != null && !session.taskUuid().isBlank()) {
                ids.add(session.taskUuid());
            }
        }));
        return ids;
    }

    private boolean matchesExpectedContext(OfficialUsageRecord record) {
        boolean workspaceMatches = expectedWorkspaceId == null || expectedWorkspaceId.isBlank()
                || expectedWorkspaceId.equals(record.workspaceId());
        boolean modelMatches = expectedModel == null || expectedModel.isBlank()
                || expectedModel.equals(record.model());
        return workspaceMatches && modelMatches && ("ws".equals(record.protocol()) || "webrtc".equals(record.protocol()));
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SyncResult(
            int scanned,
            int accepted,
            int duplicate,
            int unbound,
            int rejectedContext,
            int rejectedSchema,
            int imported,
            int providerDuplicates,
            int matched,
            int unmatched,
            Instant syncedAt) {}
}
