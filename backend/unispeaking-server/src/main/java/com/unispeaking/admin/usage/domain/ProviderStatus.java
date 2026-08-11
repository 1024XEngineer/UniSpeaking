package com.unispeaking.admin.usage.domain;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProviderStatus(
        JsonNode lastScan,
        Map<String, JsonNode> files,
        List<JsonNode> unmatchedRecords,
        int recordCount,
        String inboxDir,
        Double scanSeconds,
        Boolean running) {
    public ProviderStatus {
        files = files == null ? Map.of() : Map.copyOf(files);
        unmatchedRecords = unmatchedRecords == null ? List.of() : List.copyOf(unmatchedRecords);
    }
}
