package com.unispeaking.admin.usage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unispeaking.admin.observability.AlibabaObservabilityStatus;
import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.ProviderStatus;
import com.unispeaking.admin.usage.domain.UsageSession;
import com.unispeaking.admin.usage.domain.UsageSnapshot;
import com.unispeaking.admin.usage.domain.UsageUser;
import com.unispeaking.admin.usage.ports.UsageDataSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminUsageQueryServiceTest {
    @Test
    void aggregatesNullUsageCostAndStatusAlongsideNormalSummaryValues() {
        var clientUsage = new ModelUsage(1, 77, 40, 37, 20, 20, 17, 20);
        var officialUsage = new ModelUsage(2, 88, 45, 43, 25, 20, 19, 24);
        var userWithoutUsage = new UsageUser(
                "user-null", "Null User", "free", "Free", "active", "2026-08-11",
                100.1234, 20.5555, 0, 20.5555, 79.5678, null, null, 4,
                List.of(session("connecting", null, null, null, null, null),
                        session("waiting_client", null, null, null, null, null),
                        session("active", null, null, null, null, null),
                        session(null, null, null, null, null, null)),
                null, null, null, Map.of());
        var userWithUsage = new UsageUser(
                "user-values", "Value User", "pro", "Pro", "active", "2026-08-11",
                10.0006, 2.3456, 0, 2.3456, 7.655, null, null, 1,
                List.of(session("completed", clientUsage, officialUsage, 12L, "0.5", "MATCHED")),
                clientUsage, officialUsage, "1.25",
                Map.of("PENDING", 1, "MATCHED", 2, "MISMATCH", 3));

        var summary = service(source(List.of(userWithoutUsage, userWithUsage)), alibaba(false, "project"))
                .summary();

        assertThat(summary.totalUsers()).isEqualTo(2);
        assertThat(summary.activeSessions()).isEqualTo(3);
        assertThat(summary.quotaSeconds()).isEqualTo(110.124);
        assertThat(summary.usedSeconds()).isEqualTo(22.901);
        assertThat(summary.remainingSeconds()).isEqualTo(87.223);
        assertThat(summary.clientTokens()).isEqualTo(77);
        assertThat(summary.officialTokens()).isEqualTo(88);
        assertThat(summary.estimatedCostCny()).isEqualTo("1.25");
        assertThat(summary.reconciliationPending()).isEqualTo(1);
        assertThat(summary.reconciliationMatched()).isEqualTo(2);
        assertThat(summary.reconciliationMismatch()).isEqualTo(3);
        assertThat(summary.generatedAt()).isNotBlank();
    }

    @Test
    void reportsMissingSlsProjectSeparatelyFromConfiguredRamCredentials() {
        var source = source(List.of());
        var alibaba = alibaba(true, "replace-with-sls-project");

        var sls = service(source, alibaba).dataSources().sources().stream()
                .filter(item -> item.code().equals("ALIYUN_SLS"))
                .findFirst()
                .orElseThrow();

        assertThat(sls.state()).isEqualTo("CONFIGURATION_REQUIRED");
        assertThat(sls.detail()).contains("缺少 SLS Project");
        assertThat(sls.detail()).doesNotContain("缺少 RAM AccessKey");
        assertThat(sls.detail()).contains("bailian-model-inference-log");
    }

    @Test
    void returnsUsersAndRaisesNotFoundForAnUnknownUser() {
        var user = user("user-1", List.of());
        var service = service(source(List.of(user)), alibaba(false, "project"));

        assertThat(service.users().users()).containsExactly(user);
        assertThat(service.user("user-1")).isSameAs(user);
        assertThatThrownBy(() -> service.user("missing"))
                .isInstanceOf(UsageUserNotFoundException.class)
                .hasMessage("找不到用户：missing");
    }

    @Test
    void flattensSessionsAndMapsNullUsageAndStatusToPendingReconciliationDefaults() {
        var clientUsage = new ModelUsage(1, 100, 60, 40, 30, 30, 20, 20);
        var officialUsage = new ModelUsage(1, 105, 62, 43, 32, 30, 21, 22);
        var matched = session("completed", clientUsage, officialUsage, 900L, "0.125", "MATCHED");
        var pending = session("active", null, null, null, null, null);
        var service = service(source(List.of(user("user-1", List.of(matched, pending)))),
                alibaba(false, "project"));

        assertThat(service.sessions().sessions()).containsExactly(matched, pending);

        var records = service.reconciliation().records();
        assertThat(records).hasSize(2);
        assertThat(records.get(0).userId()).isEqualTo("user-1");
        assertThat(records.get(0).clientTokens()).isEqualTo(100);
        assertThat(records.get(0).officialTokens()).isEqualTo(105);
        assertThat(records.get(0).clientUsage()).isEqualTo(clientUsage);
        assertThat(records.get(0).officialUsage()).isEqualTo(officialUsage);
        assertThat(records.get(0).officialDurationMs()).isEqualTo(900L);
        assertThat(records.get(0).estimatedCostCny()).isEqualTo("0.125");
        assertThat(records.get(0).status()).isEqualTo("MATCHED");

        var pendingRecord = records.get(1);
        assertThat(pendingRecord.clientTokens()).isZero();
        assertThat(pendingRecord.officialTokens()).isZero();
        assertThat(pendingRecord.clientUsage()).isEqualTo(zeroUsage());
        assertThat(pendingRecord.officialUsage()).isEqualTo(zeroUsage());
        assertThat(pendingRecord.status()).isEqualTo("PENDING");
        assertThat(pendingRecord.reasons()).isEmpty();
    }

    @Test
    void reportsOnlineDefaultsAndEnabledPrometheusForTheUserDataSource() {
        var dataSources = service(source(List.of(user("user-1", List.of()))),
                alibaba(true, "project", true))
                .dataSources().sources();

        assertThat(dataSources).extracting(AdminUsageQueryService.DataSourceState::code)
                .containsExactly("POSTGRES", "ALIYUN_SLS", "ALIYUN_PROMETHEUS");
        assertThat(dataSources.get(0).name()).isEqualTo("PostgreSQL 用户数据库");
        assertThat(dataSources.get(0).state()).isEqualTo("ONLINE");
        assertThat(dataSources.get(0).detail()).contains("当前 1 个账户");
        assertThat(dataSources.get(1).state()).isEqualTo("READY");
        assertThat(dataSources.get(1).detail()).isEqualTo("cn-beijing · project · bailian-model-inference-log");
        assertThat(dataSources.get(2).state()).isEqualTo("ENABLED");
    }

    @Test
    void reportsOfflineUserDataSourceAndAllMissingSlsConfiguration() {
        UsageDataSource source = () -> {
            throw new UsageSourceUnavailableException("数据库不可用", null);
        };

        var dataSources = service(source, alibaba(false, null)).dataSources().sources();

        assertThat(dataSources.get(0).state()).isEqualTo("OFFLINE");
        assertThat(dataSources.get(0).detail()).isEqualTo("数据库不可用");
        assertThat(dataSources.get(1).state()).isEqualTo("CONFIGURATION_REQUIRED");
        assertThat(dataSources.get(1).detail())
                .startsWith("缺少 RAM AccessKey、缺少 SLS Project · cn-beijing · 未配置 · bailian-model-inference-log");
        assertThat(dataSources.get(2).state()).isEqualTo("DISABLED");
    }

    @Test
    void distinguishesEverySlsConfigurationCombination() {
        assertSls(alibaba(false, null), "CONFIGURATION_REQUIRED", "缺少 RAM AccessKey、缺少 SLS Project");
        assertSls(alibaba(false, "project"), "CONFIGURATION_REQUIRED", "缺少 RAM AccessKey");
        assertSls(alibaba(true, "  "), "CONFIGURATION_REQUIRED", "缺少 SLS Project");
        assertSls(alibaba(true, "replace-with-project"), "CONFIGURATION_REQUIRED", "缺少 SLS Project");
        assertSls(alibaba(true, "project"), "READY", "cn-beijing · project · bailian-model-inference-log");
    }

    private static void assertSls(AlibabaObservabilityStatus alibaba, String state, String detail) {
        var sls = service(source(List.of()), alibaba).dataSources().sources().get(1);
        assertThat(sls.state()).isEqualTo(state);
        assertThat(sls.detail()).contains(detail);
    }

    private static AdminUsageQueryService service(UsageDataSource source, AlibabaObservabilityStatus alibaba) {
        return new AdminUsageQueryService(source, alibaba);
    }

    private static UsageDataSource source(List<UsageUser> users) {
        return () -> new UsageSnapshot(
                users, null, new ProviderStatus(null, Map.of(), List.of(), 0, "postgres", null, false));
    }

    private static AlibabaObservabilityStatus alibaba(boolean credentialsConfigured, String project) {
        return alibaba(credentialsConfigured, project, false);
    }

    private static AlibabaObservabilityStatus alibaba(
            boolean credentialsConfigured, String project, boolean prometheusEnabled) {
        return new AlibabaObservabilityStatus(
                "cn-beijing", project, "audit", "bailian-model-inference-log",
                credentialsConfigured, prometheusEnabled);
    }

    private static UsageUser user(String userId, List<UsageSession> sessions) {
        return new UsageUser(
                userId, "User", "free", "Free", "active", "2026-08-11",
                600, 0, 0, 0, 600, null, null, sessions.size(), sessions,
                zeroUsage(), zeroUsage(), "0", Map.of());
    }

    private static UsageSession session(
            String status, ModelUsage clientUsage, ModelUsage officialUsage,
            Long officialDurationMs, String estimatedCostCny, String reconciliationStatus) {
        return new UsageSession(
                "session-" + String.valueOf(status), "user-1", "free", status, 0, 600,
                null, null, null, null, null, clientUsage, officialUsage, officialDurationMs,
                estimatedCostCny, "UNAVAILABLE", reconciliationStatus,
                List.of(), null);
    }

    private static ModelUsage zeroUsage() {
        return new ModelUsage(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
