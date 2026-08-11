package com.unispeaking.admin.usage.web;

import com.unispeaking.admin.usage.application.AdminUsageQueryService;
import com.unispeaking.admin.usage.application.AdminUsageQueryService.DashboardSummary;
import com.unispeaking.admin.usage.application.AdminUsageQueryService.DataSourcesResponse;
import com.unispeaking.admin.usage.application.AdminUsageQueryService.ReconciliationResponse;
import com.unispeaking.admin.usage.application.AdminUsageQueryService.SessionsResponse;
import com.unispeaking.admin.usage.application.AdminUsageQueryService.UsersResponse;
import com.unispeaking.admin.usage.domain.UsageUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public final class AdminUsageController {
    private final AdminUsageQueryService service;

    public AdminUsageController(AdminUsageQueryService service) {
        this.service = service;
    }

    @GetMapping("/dashboard/summary")
    DashboardSummary summary() { return service.summary(); }

    @GetMapping("/users")
    UsersResponse users() { return service.users(); }

    @GetMapping("/users/{userId}")
    UsageUser user(@PathVariable String userId) { return service.user(userId); }

    @GetMapping("/realtime/sessions")
    SessionsResponse sessions() { return service.sessions(); }

    @GetMapping("/reconciliation/records")
    ReconciliationResponse reconciliation() { return service.reconciliation(); }

    @GetMapping("/data-sources")
    DataSourcesResponse dataSources() { return service.dataSources(); }
}
