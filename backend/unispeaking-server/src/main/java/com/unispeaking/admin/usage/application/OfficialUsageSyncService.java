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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Service
@ConditionalOnBean(OfficialUsageSink.class)
public final class OfficialUsageSyncService {
    private static final Logger log = LoggerFactory.getLogger(OfficialUsageSyncService.class);
    private final OfficialUsageLogSource logSource;
    private final OfficialUsageSink sink;
    private final UsageDataSource usageDataSource;
    private final AliyunInferenceLogParser parser;
    private final Clock clock;
    private final String expectedWorkspaceId;
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
        this.lookbackSeconds = lookbackSeconds;
    }

    public SyncResult syncNow() {
        Instant now = clock.instant();
        Instant from = now.minusSeconds(lookbackSeconds);
        Instant to = now.plusSeconds(1);
        List<String> rawLogs = logSource.loadLogs(from, to);
        Set<String> localTaskUuids = localTaskUuids();
        Set<String> localRequestIds = usageDataSource.localProviderRequestIds();
        var unique = new LinkedHashMap<String, OfficialUsageRecord>();
        int rejectedSchema = 0;
        int rejectedContext = 0;
        int unbound = 0;
        int duplicate = 0;
        var schemaRejectionReasons = new LinkedHashMap<String, Integer>();
        var contextRejectionReasons = new LinkedHashMap<String, Integer>();

        for (String raw : rawLogs) {
            OfficialUsageRecord record;
            try {
                record = parser.parse(raw);
            } catch (OfficialUsageSchemaException exception) {
                rejectedSchema++;
                schemaRejectionReasons.merge(exception.getMessage(), 1, Integer::sum);
                continue;
            }
            if (!matchesExpectedContext(record)) {
                rejectedContext++;
                String reason = contextRejectionReason(record);
                contextRejectionReasons.merge(reason, 1, Integer::sum);
                continue;
            }
            boolean realtime = "ws".equals(record.protocol()) || "webrtc".equals(record.protocol());
            boolean locallyBound = realtime
                    ? localTaskUuids.contains(record.taskUuid())
                    : localRequestIds.contains(record.requestId());
            if (!locallyBound) {
                unbound++;
                continue;
            }
            if (unique.putIfAbsent(record.requestId(), record) != null) {
                duplicate++;
            }
        }

        List<OfficialUsageRecord> accepted = new ArrayList<>(unique.values());
        if (!schemaRejectionReasons.isEmpty()) {
            log.warn("阿里云官方用量记录因格式无效被丢弃: {}", schemaRejectionReasons);
        }
        if (!contextRejectionReasons.isEmpty()) {
            log.warn("阿里云官方用量记录因上下文不匹配被丢弃: {}", contextRejectionReasons);
        }
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
        boolean supportedProtocol = "ws".equals(record.protocol())
                || "webrtc".equals(record.protocol())
                || "http".equals(record.protocol());
        // Provider error responses still carry billable/reconcilable identifiers;
        // status_code is informational and must not prevent matching.
        return workspaceMatches && supportedProtocol;
    }

    private String contextRejectionReason(OfficialUsageRecord record) {
        var reasons = new ArrayList<String>();
        if (expectedWorkspaceId != null && !expectedWorkspaceId.isBlank()
                && !expectedWorkspaceId.equals(record.workspaceId())) {
            reasons.add("workspace_mismatch");
        }
        if (!("ws".equals(record.protocol()) || "webrtc".equals(record.protocol())
                || "http".equals(record.protocol()))) {
            reasons.add("unsupported_protocol");
        }
        return reasons.isEmpty() ? "unknown" : String.join("+", reasons);
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
