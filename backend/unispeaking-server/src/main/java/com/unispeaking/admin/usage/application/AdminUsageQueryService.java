package com.unispeaking.admin.usage.application;

import com.unispeaking.admin.observability.AlibabaObservabilityStatus;
import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.UsageSession;
import com.unispeaking.admin.usage.domain.UsageUser;
import com.unispeaking.admin.usage.ports.UsageDataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Service
public final class AdminUsageQueryService {
    private static final Set<String> ACTIVE_SESSION_STATUSES = Set.of(
            "created", "connecting", "connected", "waiting_client", "active");
    private final UsageDataSource usageDataSource;
    private final AlibabaObservabilityStatus alibaba;

    public AdminUsageQueryService(UsageDataSource usageDataSource, AlibabaObservabilityStatus alibaba) {
        this.usageDataSource = usageDataSource;
        this.alibaba = alibaba;
    }

    public DashboardSummary summary() {
        var users = usageDataSource.loadSnapshot().users();
        int activeSessions = 0;
        double quota = 0;
        double used = 0;
        double remaining = 0;
        long clientTokens = 0;
        long officialTokens = 0;
        int pending = 0;
        int matched = 0;
        int mismatch = 0;
        BigDecimal estimatedCost = BigDecimal.ZERO;
        for (var user : users) {
            quota += user.quotaSeconds();
            used += user.usedSeconds();
            remaining += user.remainingSeconds();
            clientTokens += totalTokens(user.modelUsage());
            officialTokens += totalTokens(user.officialUsage());
            pending += user.reconciliationCounts().getOrDefault("PENDING", 0);
            matched += user.reconciliationCounts().getOrDefault("MATCHED", 0);
            mismatch += user.reconciliationCounts().getOrDefault("MISMATCH", 0);
            if (user.estimatedCostCny() != null) {
                estimatedCost = estimatedCost.add(new BigDecimal(user.estimatedCostCny()));
            }
            activeSessions += (int) user.sessions().stream()
                    .filter(session -> session.status() != null
                            && ACTIVE_SESSION_STATUSES.contains(
                                    session.status().trim().toLowerCase(java.util.Locale.ROOT)))
                    .count();
        }
        return new DashboardSummary(
                users.size(), activeSessions, round(quota), round(used), round(remaining),
                clientTokens, officialTokens, estimatedCost.toPlainString(),
                pending, matched, mismatch, Instant.now().toString());
    }

    public UsersResponse users() {
        return new UsersResponse(usageDataSource.loadSnapshot().users());
    }

    public UsageUser user(String userId) {
        return usageDataSource.loadSnapshot().users().stream()
                .filter(user -> user.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new UsageUserNotFoundException(userId));
    }

    public SessionsResponse sessions() {
        var sessions = usageDataSource.loadSnapshot().users().stream()
                .flatMap(user -> user.sessions().stream())
                .toList();
        return new SessionsResponse(sessions);
    }

    public ReconciliationResponse reconciliation() {
        var records = usageDataSource.loadSnapshot().users().stream()
                .flatMap(user -> user.sessions().stream().map(session -> toReconciliation(user, session)))
                .toList();
        return new ReconciliationResponse(records);
    }

    public DataSourcesResponse dataSources() {
        DataSourceState userDatabase;
        try {
            var snapshot = usageDataSource.loadSnapshot();
            userDatabase = new DataSourceState(
                    usageDataSource.sourceCode(), usageDataSource.sourceName(), "ONLINE",
                    usageDataSource.sourceDetail() + "，当前 " + snapshot.users().size() + " 个账户");
        } catch (UsageSourceUnavailableException exception) {
            userDatabase = new DataSourceState(
                    usageDataSource.sourceCode(), usageDataSource.sourceName(), "OFFLINE", exception.getMessage());
        }
        var slsState = alibaba.credentialsConfigured() ? "READY" : "CONFIGURATION_REQUIRED";
        var slsLocation = alibaba.region() + " · " + alibaba.project() + " · " + alibaba.inferenceLogstore();
        var slsDetail = alibaba.credentialsConfigured()
                ? slsLocation
                : "缺少 RAM AccessKey · " + slsLocation;
        var prometheusState = alibaba.prometheusEnabled() ? "ENABLED" : "DISABLED";
        return new DataSourcesResponse(List.of(
                userDatabase,
                new DataSourceState("ALIYUN_SLS", "阿里云 SLS", slsState, slsDetail),
                new DataSourceState("ALIYUN_PROMETHEUS", "阿里云 Prometheus", prometheusState, "高级监控聚合指标")));
    }

    private static ReconciliationRecord toReconciliation(UsageUser user, UsageSession session) {
        ModelUsage clientUsage = usageOrZero(session.modelUsage());
        ModelUsage officialUsage = usageOrZero(session.officialUsage());
        return new ReconciliationRecord(
                user.userId(), session.sessionId(), session.temporaryKeyId(), session.taskUuid(),
                session.providerRequestId(), clientUsage.totalTokens(), officialUsage.totalTokens(),
                clientUsage, officialUsage,
                session.officialDurationMs(), session.estimatedCostCny(),
                session.reconciliationStatus() == null ? "PENDING" : session.reconciliationStatus(),
                session.reconciliationReasons());
    }

    private static ModelUsage usageOrZero(ModelUsage usage) {
        return usage == null ? new ModelUsage(0, 0, 0, 0, 0, 0, 0, 0) : usage;
    }

    private static long totalTokens(ModelUsage usage) {
        return usage == null ? 0 : usage.totalTokens();
    }

    private static double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DashboardSummary(
            int totalUsers,
            int activeSessions,
            double quotaSeconds,
            double usedSeconds,
            double remainingSeconds,
            long clientTokens,
            long officialTokens,
            String estimatedCostCny,
            int reconciliationPending,
            int reconciliationMatched,
            int reconciliationMismatch,
            String generatedAt) {}

    public record UsersResponse(List<UsageUser> users) {}
    public record SessionsResponse(List<UsageSession> sessions) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReconciliationRecord(
            String userId,
            String sessionId,
            String temporaryKeyId,
            String taskUuid,
            String requestId,
            long clientTokens,
            long officialTokens,
            ModelUsage clientUsage,
            ModelUsage officialUsage,
            Long officialDurationMs,
            String estimatedCostCny,
            String status,
            List<String> reasons) {}

    public record ReconciliationResponse(List<ReconciliationRecord> records) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DataSourceState(String code, String name, String state, String detail) {}
    public record DataSourcesResponse(List<DataSourceState> sources) {}
}
