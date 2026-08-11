package com.unispeaking.admin.usage.ports;

import com.unispeaking.admin.usage.domain.UsageSnapshot;

public interface UsageDataSource {
    UsageSnapshot loadSnapshot();

    default String sourceCode() { return "POSTGRES"; }

    default String sourceName() { return "PostgreSQL 用户数据库"; }

    default String sourceDetail() { return "真实注册用户、权益和会话归属"; }
}
