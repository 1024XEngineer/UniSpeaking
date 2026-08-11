package com.unispeaking.auth;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class EmailAuthConfiguration {

    @Bean(name = "userPasswordEncoder")
    PasswordEncoder userPasswordEncoder() {
        // The legacy business login already stores BCrypt hashes in "user".
        // Email login must use the same encoder so both flows share one identity.
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
}
