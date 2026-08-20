package com.unispeaking.admin.usage.domain;

public record OfficialUsageRecord(
        String requestId,
        String taskUuid,
        long startedAtEpochMs,
        long durationMs,
        String statusCode,
        String model,
        String workspaceId,
        String apiKeyId,
        String protocol,
        ModelUsage usage,
        long characters) {

    public OfficialUsageRecord(
            String requestId,
            String taskUuid,
            long startedAtEpochMs,
            long durationMs,
            String statusCode,
            String model,
            String workspaceId,
            String apiKeyId,
            String protocol,
            ModelUsage usage) {
        this(requestId, taskUuid, startedAtEpochMs, durationMs, statusCode, model,
                workspaceId, apiKeyId, protocol, usage, 0);
    }
}
