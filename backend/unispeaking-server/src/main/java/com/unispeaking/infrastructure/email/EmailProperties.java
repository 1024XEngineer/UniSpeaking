package com.unispeaking.infrastructure.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "unispeaking.auth.email")
public record EmailProperties(
        boolean enabled,
        String host,
        int port,
        String username,
        String password,
        String fromAddress,
        String fromName,
        boolean ssl) {
}
