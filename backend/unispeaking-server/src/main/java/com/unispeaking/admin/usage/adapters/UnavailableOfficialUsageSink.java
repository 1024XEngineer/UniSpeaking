package com.unispeaking.admin.usage.adapters;

import com.unispeaking.admin.usage.application.UsageSourceUnavailableException;
import com.unispeaking.admin.usage.ports.OfficialUsageSink;
import java.util.List;

/** Explicit local boundary when no official-usage write target is configured. */
public final class UnavailableOfficialUsageSink implements OfficialUsageSink {
    @Override
    public ImportResult importRecords(List<com.unispeaking.admin.usage.domain.OfficialUsageRecord> records) {
        throw new UsageSourceUnavailableException(
                "未配置官方用量写入目标（启用 FreeTalk 官方用量集成后才能导入）", null);
    }
}
