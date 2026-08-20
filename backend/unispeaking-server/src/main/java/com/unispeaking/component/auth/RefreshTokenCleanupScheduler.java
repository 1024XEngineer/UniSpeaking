package com.unispeaking.component.auth;

import com.unispeaking.service.auth.RefreshTokenService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCleanupScheduler {
    private final RefreshTokenService service;
    public RefreshTokenCleanupScheduler(RefreshTokenService service) { this.service = service; }
    @Scheduled(cron = "${auth.jwt.refresh-cleanup-cron:0 20 3 * * *}")
    public void cleanup() { service.cleanup(); }
}
