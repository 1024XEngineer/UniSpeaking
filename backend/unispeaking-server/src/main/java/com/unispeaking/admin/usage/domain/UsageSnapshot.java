package com.unispeaking.admin.usage.domain;

import tools.jackson.databind.JsonNode;
import java.util.List;

public record UsageSnapshot(List<UsageUser> users, JsonNode vendorTotal, ProviderStatus provider) {
    public UsageSnapshot {
        users = users == null ? List.of() : List.copyOf(users);
    }
}
