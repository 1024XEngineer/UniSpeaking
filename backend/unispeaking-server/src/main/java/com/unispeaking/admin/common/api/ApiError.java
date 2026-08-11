package com.unispeaking.admin.common.api;

public record ApiError(ErrorBody error, String request_id) {
    public record ErrorBody(String code, String message) {
    }
}
