package com.unispeaking.admin.usage.web;

import com.unispeaking.admin.usage.application.AdminEntitlementService;
import com.unispeaking.admin.usage.application.AdminEntitlementService.EntitlementView;
import com.unispeaking.admin.usage.application.AdminEntitlementService.UpdateRequest;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public final class AdminEntitlementController {
    private final AdminEntitlementService service;

    public AdminEntitlementController(AdminEntitlementService service) {
        this.service = service;
    }

    @PatchMapping("/{userId}/entitlement")
    EntitlementView updateEntitlement(
            @PathVariable String userId,
            @RequestBody UpdateRequest request) {
        return service.update(userId, request);
    }
}
