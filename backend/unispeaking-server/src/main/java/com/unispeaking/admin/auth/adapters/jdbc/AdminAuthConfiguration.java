package com.unispeaking.admin.auth.adapters.jdbc;

import com.unispeaking.admin.auth.application.AuthService;
import com.unispeaking.admin.auth.domain.AdminAccount;
import com.unispeaking.admin.auth.domain.AdminRole;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

/** Admin repositories share the main backend DataSource and transaction boundary. */
@Configuration
@ConditionalOnProperty(name = "unispeaking.admin.persistence", havingValue = "postgres", matchIfMissing = true)
public class AdminAuthConfiguration {

    @Bean(name = "adminPasswordEncoder")
    PasswordEncoder adminPasswordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    JdbcAdminIdentityRepository adminIdentities(
            JdbcTemplate jdbc,
            @Qualifier("adminPasswordEncoder") PasswordEncoder passwordEncoder,
            @Value("${unispeaking.admin.bootstrap-password:}") String bootstrapPassword) {
        var repository = new JdbcAdminIdentityRepository(jdbc);
        if (StringUtils.hasText(bootstrapPassword)) {
            repository.saveOrUpdateBootstrap(new AdminAccount(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "admin@unispeaking.local",
                    passwordEncoder.encode(bootstrapPassword),
                    AdminRole.SUPER_ADMIN,
                    true),
                    Instant.now());
        }
        return repository;
    }

    @Bean
    JdbcAdminSessionRepository adminSessions(JdbcTemplate jdbc) {
        return new JdbcAdminSessionRepository(jdbc);
    }

    @Bean
    AuthService adminAuthService(
            JdbcAdminIdentityRepository identities,
            JdbcAdminSessionRepository sessions,
            @Qualifier("adminPasswordEncoder") PasswordEncoder passwordEncoder,
            @Value("${unispeaking.admin.session-idle-seconds:1800}") long idleSeconds,
            @Value("${unispeaking.admin.session-absolute-seconds:28800}") long absoluteSeconds) {
        return new AuthService(identities, sessions, passwordEncoder, Clock.systemUTC(), idleSeconds, absoluteSeconds);
    }
}
