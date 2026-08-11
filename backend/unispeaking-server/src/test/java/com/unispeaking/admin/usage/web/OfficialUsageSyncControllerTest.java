package com.unispeaking.admin.usage.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.unispeaking.admin.usage.adapters.aliyun.AliyunInferenceLogParser;
import com.unispeaking.admin.usage.application.OfficialUsageSyncService;
import com.unispeaking.admin.usage.domain.ProviderStatus;
import com.unispeaking.admin.usage.domain.UsageSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class OfficialUsageSyncControllerTest {
    @Test
    void requiresExplicitAdminActionHeaderAndReturnsSyncCounters() throws Exception {
        var sourceFrom = new Instant[1];
        var service = new OfficialUsageSyncService(
                (from, to) -> {
                    sourceFrom[0] = from;
                    return List.of();
                },
                records -> new com.unispeaking.admin.usage.ports.OfficialUsageSink.ImportResult(0, 0, 0, 0),
                () -> new UsageSnapshot(List.of(), null,
                        new ProviderStatus(null, Map.of(), List.of(), 0, "/tmp/inbox", 3d, true)),
                new AliyunInferenceLogParser(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-07-20T08:10:00Z"), ZoneOffset.UTC),
                "ws-local", "qwen3.5-omni-flash-realtime", 600);
        var mvc = MockMvcBuilders.standaloneSetup(new OfficialUsageSyncController(service)).build();

        mvc.perform(post("/api/admin/data-sources/aliyun-sls/sync"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/data-sources/aliyun-sls/sync")
                        .header("X-Admin-Action", "sync-official-usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanned").value(0))
                .andExpect(jsonPath("$.accepted").value(0))
                .andExpect(jsonPath("$.rejected_context").value(0))
                .andExpect(jsonPath("$.matched").value(0));

        assertThat(sourceFrom[0]).isEqualTo(Instant.parse("2026-07-20T08:00:00Z"));
    }
}
