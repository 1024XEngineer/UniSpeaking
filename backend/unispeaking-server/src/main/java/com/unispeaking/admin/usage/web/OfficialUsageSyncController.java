package com.unispeaking.admin.usage.web;

import com.unispeaking.admin.usage.application.OfficialUsageSyncService;
import com.unispeaking.admin.usage.application.OfficialUsageSyncService.SyncResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnBean(OfficialUsageSyncService.class)
@RequestMapping("/api/admin/data-sources/aliyun-sls")
public final class OfficialUsageSyncController {
    private final OfficialUsageSyncService service;

    public OfficialUsageSyncController(OfficialUsageSyncService service) {
        this.service = service;
    }

    @PostMapping("/sync")
    SyncResult sync(@RequestHeader(value = "X-Admin-Action", defaultValue = "") String action) {
        if (!"sync-official-usage".equals(action)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "缺少管理操作确认头");
        }
        return service.syncNow();
    }
}
