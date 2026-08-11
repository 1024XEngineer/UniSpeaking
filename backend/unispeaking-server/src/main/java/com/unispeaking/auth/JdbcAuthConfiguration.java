package com.unispeaking.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@ConditionalOnProperty(name = "unispeaking.auth.persistence", havingValue = "postgres")
public class JdbcAuthConfiguration {
    @Bean
    EmailAuthStore jdbcEmailAuthStore(JdbcTemplate jdbcTemplate) {
        return new JdbcEmailAuthStore(jdbcTemplate);
    }
}
