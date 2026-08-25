package com.unispeaking.admin.monitoring;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/monitoring")
public class MonitoringAdminController {
    private final MonitoringAdminService service;

    public MonitoringAdminController(MonitoringAdminService service) { this.service = service; }

    @GetMapping("/overview")
    MonitoringAdminService.MonitoringResponse overview() { return service.overview(); }
}
