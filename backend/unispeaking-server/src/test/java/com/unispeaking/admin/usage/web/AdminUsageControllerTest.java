package com.unispeaking.admin.usage.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.admin.observability.AlibabaObservabilityStatus;
import com.unispeaking.admin.usage.application.AdminUsageQueryService;
import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.ProviderStatus;
import com.unispeaking.admin.usage.domain.UsageSession;
import com.unispeaking.admin.usage.domain.UsageSnapshot;
import com.unispeaking.admin.usage.domain.UsageUser;
import com.unispeaking.admin.usage.ports.UsageDataSource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminUsageControllerTest {
    @Test
    void exposesSummaryUsersSessionsReconciliationAndSourceStates() throws Exception {
        ModelUsage client = new ModelUsage(1, 200, 150, 50, 100, 50, 20, 30);
        ModelUsage official = new ModelUsage(1, 205, 153, 52, 102, 51, 21, 31);
        UsageSession session = new UsageSession(
                "local-1", "user-01", "free", "ended", 81.2, 98.8,
                "key-id", "fingerprint", 1784092500L, "task-1", "request-1",
                client, official, 81215L, "0.0265", "priced", "MISMATCH",
                List.of("client_official_tokens_differ"), "user_end");
        UsageUser user = new UsageUser(
                "user-01", "User 01", "free", "Free", "active", "2026-07-20",
                180, 81.2, 0, 81.2, 98.8, 1784563200d, null, 1,
                List.of(session), client, official, "0.0265",
                Map.of("PENDING", 0, "MATCHED", 0, "MISMATCH", 1));
        UsageDataSource source = () -> new UsageSnapshot(
                List.of(user), null,
                new ProviderStatus(null, Map.of(), List.of(), 1, "/tmp/inbox", 3d, true));
        AlibabaObservabilityStatus alibaba = new AlibabaObservabilityStatus(
                "cn-beijing",
                "aliyun-product-data-example-cn-beijing",
                "bailian-model-audit-log",
                "bailian-model-inference-log",
                false,
                true);
        var service = new AdminUsageQueryService(source, alibaba);
        var mvc = MockMvcBuilders.standaloneSetup(new AdminUsageController(service)).build();

        mvc.perform(get("/api/admin/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_users").value(1))
                .andExpect(jsonPath("$.used_seconds").value(81.2))
                .andExpect(jsonPath("$.client_tokens").value(200))
                .andExpect(jsonPath("$.official_tokens").value(205))
                .andExpect(jsonPath("$.reconciliation_mismatch").value(1));

        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].user_id").value("user-01"));

        mvc.perform(get("/api/admin/realtime/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].task_uuid").value("task-1"));

        mvc.perform(get("/api/admin/reconciliation/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].request_id").value("request-1"))
                .andExpect(jsonPath("$.records[0].client_usage.input_tokens").value(150))
                .andExpect(jsonPath("$.records[0].client_usage.output_tokens").value(50))
                .andExpect(jsonPath("$.records[0].official_usage.input_tokens").value(153))
                .andExpect(jsonPath("$.records[0].official_usage.output_tokens").value(52))
                .andExpect(jsonPath("$.records[0].status").value("MISMATCH"));

        mvc.perform(get("/api/admin/data-sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0].code").value("POSTGRES"))
                .andExpect(jsonPath("$.sources[0].name").value("PostgreSQL 用户数据库"))
                .andExpect(jsonPath("$.sources[0].state").value("ONLINE"))
                .andExpect(jsonPath("$.sources[1].code").value("ALIYUN_SLS"))
                .andExpect(jsonPath("$.sources[1].state").value("CONFIGURATION_REQUIRED"))
                .andExpect(jsonPath("$.sources[1].detail").value(org.hamcrest.Matchers.containsString("缺少 RAM AccessKey")))
                .andExpect(jsonPath("$.sources[1].detail").value(org.hamcrest.Matchers.containsString("bailian-model-inference-log")));
    }
}
