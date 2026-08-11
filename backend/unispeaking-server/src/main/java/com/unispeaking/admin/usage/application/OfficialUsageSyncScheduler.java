package com.unispeaking.admin.usage.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "unispeaking.integrations.aliyun.sync-enabled", havingValue = "true")
@ConditionalOnBean(OfficialUsageSyncService.class)
public final class OfficialUsageSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(OfficialUsageSyncScheduler.class);
    private final OfficialUsageSyncService service;

    public OfficialUsageSyncScheduler(OfficialUsageSyncService service) {
        this.service = service;
    }

    @Scheduled(
            initialDelayString = "${unispeaking.integrations.aliyun.sync-fixed-delay-ms:60000}",
            fixedDelayString = "${unispeaking.integrations.aliyun.sync-fixed-delay-ms:60000}")
    void sync() {
        try {
            var result = service.syncNow();
            log.info("阿里云官方用量同步完成: scanned={}, accepted={}, matched={}",
                    result.scanned(), result.accepted(), result.matched());
        } catch (RuntimeException exception) {
            log.warn("阿里云官方用量同步失败: {}", exception.getMessage());
        }
    }
}
