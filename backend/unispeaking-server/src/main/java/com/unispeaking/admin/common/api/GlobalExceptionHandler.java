package com.unispeaking.admin.common.api;

import com.unispeaking.admin.auth.application.InvalidCredentialsException;
import com.unispeaking.admin.usage.application.UsageSourceUnavailableException;
import com.unispeaking.admin.usage.application.UsageUserNotFoundException;
import com.unispeaking.admin.usage.application.AdminEntitlementService.InvalidEntitlementException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.stereotype.Component;

@RestControllerAdvice
@Component("adminGlobalExceptionHandler")
public final class GlobalExceptionHandler {
    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> invalidCredentials(HttpServletRequest request) {
        var requestId = String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(
                        new ApiError.ErrorBody("AUTH_INVALID", "用户名或密码错误"),
                        requestId));
    }

    @ExceptionHandler(UsageSourceUnavailableException.class)
    ResponseEntity<ApiError> usageSourceUnavailable(
            UsageSourceUnavailableException exception,
            HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE", exception.getMessage(), request);
    }

    @ExceptionHandler(UsageUserNotFoundException.class)
    ResponseEntity<ApiError> usageUserNotFound(
            UsageUserNotFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidEntitlementException.class)
    ResponseEntity<ApiError> invalidEntitlement(
            InvalidEntitlementException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_ENTITLEMENT", exception.getMessage(), request);
    }

    private static ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        var requestId = String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE));
        return ResponseEntity.status(status)
                .body(new ApiError(new ApiError.ErrorBody(code, message), requestId));
    }
}
