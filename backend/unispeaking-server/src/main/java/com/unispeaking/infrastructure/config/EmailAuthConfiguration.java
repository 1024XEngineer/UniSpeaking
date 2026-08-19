package com.unispeaking.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class EmailAuthConfiguration {

    @Bean(name = "userPasswordEncoder")
    PasswordEncoder userPasswordEncoder() {
        // Business and email login share the same BCrypt identity in users.
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock userAuthClock() {
        return Clock.systemUTC();
    }

    @Bean
    Duration userAuthChallengeTtl() {
        return Duration.ofMinutes(10);
    }

    @Bean(name = "userAuthSessionTtl")
    Duration userAuthSessionTtl(
            @Value("${AUTH_SESSION_MAX_AGE_SECONDS:28800}") long maxAgeSeconds) {
        if (maxAgeSeconds < 60 || maxAgeSeconds > 604800) {
            throw new IllegalArgumentException("AUTH_SESSION_MAX_AGE_SECONDS must be between 60 and 604800");
        }
        return Duration.ofSeconds(maxAgeSeconds);
    }

    @Bean(name = "mobileAuthSessionIdleTtl")
    Duration mobileAuthSessionIdleTtl(
            @Value("${AUTH_MOBILE_SESSION_IDLE_SECONDS:2592000}") long idleSeconds) {
        if (idleSeconds < 3600 || idleSeconds > 31536000) {
            throw new IllegalArgumentException(
                    "AUTH_MOBILE_SESSION_IDLE_SECONDS must be between 3600 and 31536000");
        }
        return Duration.ofSeconds(idleSeconds);
    }
}
