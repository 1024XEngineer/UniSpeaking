package com.unispeaking.admin.usage.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.unispeaking.admin.observability.AlibabaObservabilityStatus;
import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.ProviderStatus;
import com.unispeaking.admin.usage.domain.UsageSession;
import com.unispeaking.admin.usage.domain.UsageSnapshot;
import com.unispeaking.admin.usage.domain.UsageUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminUsageQueryServiceTest {
	@Test
	void countsCanonicalBackendConnectionStatusesInTheDashboard() {
		var emptyUsage = new ModelUsage(0, 0, 0, 0, 0, 0, 0, 0);
		var sessions = List.of(
				session("connecting", emptyUsage),
				session("waiting_client", emptyUsage),
				session("active", emptyUsage),
				session("completed", emptyUsage));
		var user = new UsageUser(
				"user-1", "User", "free", "Free", "active", "2026-08-11",
				600, 0, 0, 0, 600, null, "active", sessions.size(), sessions,
				emptyUsage, emptyUsage, "0", Map.of());
		var source = (com.unispeaking.admin.usage.ports.UsageDataSource) () ->
				new UsageSnapshot(
						List.of(user), null,
						new ProviderStatus(null, Map.of(), List.of(), 0, "postgres", null, false));
		var alibaba = new AlibabaObservabilityStatus(
				"cn-shanghai", "project", "audit", "inference", false, false);

		var summary = new AdminUsageQueryService(source, alibaba).summary();

		assertThat(summary.activeSessions()).isEqualTo(3);
	}

	private static UsageSession session(String status, ModelUsage usage) {
		return new UsageSession(
				"session-" + status, "user-1", "free", status, 0, 600,
				null, null, null, null, null, usage, usage, null, "0", "UNAVAILABLE",
				"PENDING", List.of(), null);
	}
}
