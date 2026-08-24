package com.unispeaking.admin.usage.application;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OfficialUsageSyncSchedulerTest {
    @Test
    void runsSyncAndAcceptsResult() {
        OfficialUsageSyncService service = mock(OfficialUsageSyncService.class);
        OfficialUsageSyncService.SyncResult result = new OfficialUsageSyncService.SyncResult(
                10, 8, 1, 1, 0, 0, 7, 1, 5, 2, Instant.parse("2026-08-24T00:00:00Z"));
        org.mockito.Mockito.when(service.syncNow()).thenReturn(result);

        new OfficialUsageSyncScheduler(service).sync();

        verify(service).syncNow();
    }

    @Test
    void swallowsSyncFailureSoScheduledExecutionContinues() {
        OfficialUsageSyncService service = mock(OfficialUsageSyncService.class);
        doThrow(new IllegalStateException("temporary failure")).when(service).syncNow();

        new OfficialUsageSyncScheduler(service).sync();

        verify(service).syncNow();
    }
}
