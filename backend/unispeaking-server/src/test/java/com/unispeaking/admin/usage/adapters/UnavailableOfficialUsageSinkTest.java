package com.unispeaking.admin.usage.adapters;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unispeaking.admin.usage.application.UsageSourceUnavailableException;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnavailableOfficialUsageSinkTest {
    @Test
    void reportsMissingOfficialUsageSinkInsteadOfDroppingRecords() {
        var sink = new UnavailableOfficialUsageSink();

        assertThatThrownBy(() -> sink.importRecords(List.of()))
                .isInstanceOf(UsageSourceUnavailableException.class)
                .hasMessage("未配置官方用量写入目标（启用 FreeTalk 官方用量集成后才能导入）");
    }
}
