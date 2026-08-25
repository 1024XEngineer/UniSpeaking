package com.unispeaking.admin.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.admin.auth.application.InvalidCredentialsException;
import com.unispeaking.admin.quality.QualityIssueAdminService.QualityIssueNotFoundException;
import com.unispeaking.admin.usage.application.AdminEntitlementService.InvalidEntitlementException;
import com.unispeaking.admin.usage.application.UsageSourceUnavailableException;
import com.unispeaking.admin.usage.application.UsageUserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminGlobalExceptionHandlerTest {
    @Test
    void mapsEveryAdminExceptionAndRequestId() {
        var handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RequestIdFilter.ATTRIBUTE)).thenReturn("request-1");

        assertEquals(401, handler.invalidCredentials(request).getStatusCode().value());
        assertEquals("AUTH_INVALID", handler.invalidCredentials(request).getBody().error().code());
        assertEquals(503, handler.usageSourceUnavailable(
                new UsageSourceUnavailableException("down", null), request).getStatusCode().value());
        assertEquals(404, handler.usageUserNotFound(
                new UsageUserNotFoundException("user"), request).getStatusCode().value());
        assertEquals(400, handler.invalidEntitlement(
                new InvalidEntitlementException("invalid"), request).getStatusCode().value());
        var notFound = handler.qualityIssueNotFound(
                new QualityIssueNotFoundException(UUID.randomUUID()), request);
        assertEquals(404, notFound.getStatusCode().value());
		assertEquals("request-1", notFound.getBody().request_id());
    }
}
