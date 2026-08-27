package com.unispeaking.admin.monitoring;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
	void exposesMonitoringOverviewForRequestedRange() throws Exception {
		MonitoringAdminService service = mock(MonitoringAdminService.class);
		MonitoringAdminService.Governance governance = new MonitoringAdminService.Governance(
				2, 3, 1, 33.3, new MonitoringAdminService.Comparison(10, 8),
				new MonitoringAdminService.Comparison(900, 700),
				new MonitoringAdminService.Comparison(5, 4));
		when(service.overview("7d")).thenReturn(new MonitoringAdminService.MonitoringResponse(
				new MonitoringAdminService.Summary("UP", 1.0, 4, 800, 2, 3,
						Instant.parse("2026-08-25T00:00:00Z")), governance,
				List.of(), List.of(), List.of(), List.of(), List.of()));
		var mvc = MockMvcBuilders.standaloneSetup(new MonitoringAdminController(service)).build();

		mvc.perform(get("/api/admin/monitoring/overview").param("range", "7d"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.backendStatus").value("UP"))
				.andExpect(jsonPath("$.summary.api5xxCount24h").value(4))
				.andExpect(jsonPath("$.governance.resolvedBugs7d").value(1));
		verify(service).overview("7d");
	}
}
