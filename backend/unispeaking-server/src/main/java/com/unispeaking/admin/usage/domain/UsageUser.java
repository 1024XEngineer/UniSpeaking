package com.unispeaking.admin.usage.domain;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UsageUser(
        String userId,
        String displayName,
        String planCode,
        String planName,
        String status,
        String quotaDate,
        double quotaSeconds,
        double settledSeconds,
        double activeElapsedSeconds,
        double usedSeconds,
        double remainingSeconds,
        Double resetAt,
        String activeSessionId,
        int sessionCount,
        List<UsageSession> sessions,
        ModelUsage modelUsage,
        ModelUsage officialUsage,
        String estimatedCostCny,
        Map<String, Integer> reconciliationCounts) {
    public UsageUser {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
        reconciliationCounts = reconciliationCounts == null ? Map.of() : Map.copyOf(reconciliationCounts);
    }
}
