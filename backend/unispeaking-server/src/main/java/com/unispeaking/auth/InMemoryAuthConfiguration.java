package com.unispeaking.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Explicit test/local fallback; production deployments use the PostgreSQL adapter. */
@Configuration
@ConditionalOnProperty(name = "unispeaking.auth.persistence", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryAuthConfiguration {
    @Bean
    EmailAuthStore inMemoryEmailAuthStore() {
        return new InMemoryEmailAuthStore();
    }
}
