package com.unispeaking.admin.usage.ports;

import java.time.Instant;
import java.util.List;

public interface OfficialUsageLogSource {
    List<String> loadLogs(Instant from, Instant to);
}
