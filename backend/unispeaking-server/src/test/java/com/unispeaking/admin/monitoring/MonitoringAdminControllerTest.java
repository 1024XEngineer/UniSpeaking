package com.unispeaking.admin.monitoring;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MonitoringAdminControllerTest {

    @Test
    void exposesMonitoringOverviewEndpoint() throws Exception {
        MonitoringAdminService service = mock(MonitoringAdminService.class);
        when(service.overview()).thenReturn(new MonitoringAdminService.MonitoringResponse(
                new MonitoringAdminService.Summary("UP", 1.0, 0.2, 0.8, 2, 3, 4,
                        Instant.parse("2026-08-25T00:00:00Z")),
                List.of(), List.of(), List.of(), List.of(), List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(new MonitoringAdminController(service)).build();

        mvc.perform(get("/api/admin/monitoring/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.backendStatus").value("UP"))
                .andExpect(jsonPath("$.summary.activeAlerts").value(2));
    }
}
