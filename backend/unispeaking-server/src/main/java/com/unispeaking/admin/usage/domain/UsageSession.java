package com.unispeaking.admin.usage.domain;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UsageSession(
        String sessionId,
        String userId,
        String planCode,
        String status,
        double measuredSeconds,
        double remainingSeconds,
        String temporaryKeyId,
        String temporaryKeyFingerprint,
        Long temporaryKeyExpiresAt,
        String taskUuid,
        String providerRequestId,
        ModelUsage modelUsage,
        ModelUsage officialUsage,
        Long officialDurationMs,
        String estimatedCostCny,
        String pricingStatus,
        String reconciliationStatus,
        List<String> reconciliationReasons,
        String endReason) {
    public UsageSession {
        reconciliationReasons = reconciliationReasons == null ? List.of() : List.copyOf(reconciliationReasons);
    }
}
