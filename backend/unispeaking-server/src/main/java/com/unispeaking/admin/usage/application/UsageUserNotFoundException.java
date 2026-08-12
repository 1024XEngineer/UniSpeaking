package com.unispeaking.admin.usage.application;

public final class UsageUserNotFoundException extends RuntimeException {
    public UsageUserNotFoundException(String userId) {
        super("找不到用户：" + userId);
    }
}
