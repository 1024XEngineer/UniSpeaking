package com.unispeaking.admin.usage.ports;

import com.unispeaking.admin.usage.domain.OfficialUsageRecord;
import java.util.List;

public interface OfficialUsageSink {
    ImportResult importRecords(List<OfficialUsageRecord> records);

    record ImportResult(int imported, int duplicates, int matched, int unmatched) {}
}
