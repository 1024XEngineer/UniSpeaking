package com.unispeaking.admin.auth.adapters.memory;

import com.unispeaking.admin.auth.application.AuthService;
import com.unispeaking.admin.auth.domain.AdminAccount;
import com.unispeaking.admin.auth.domain.AdminRole;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Configuration
@Profile("default")
@ConditionalOnProperty(name = "unispeaking.admin.persistence", havingValue = "in-memory", matchIfMissing = true)
public class DevelopmentAuthConfiguration {
    @Bean(name = "adminPasswordEncoder")
    PasswordEncoder adminPasswordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    InMemoryAdminIdentityRepository adminIdentities(
            @Qualifier("adminPasswordEncoder") PasswordEncoder passwordEncoder,
            @Value("${unispeaking.admin.bootstrap-password:}") String bootstrapPassword) {
        var repository = new InMemoryAdminIdentityRepository();
        if (StringUtils.hasText(bootstrapPassword)) {
            repository.save(new AdminAccount(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    "admin@unispeaking.local",
                    passwordEncoder.encode(bootstrapPassword),
                    AdminRole.SUPER_ADMIN,
                    true));
        }
        return repository;
    }

    @Bean
    InMemoryAdminSessionRepository adminSessions() {
        return new InMemoryAdminSessionRepository();
    }

    @Bean
    AuthService adminAuthService(
            InMemoryAdminIdentityRepository identities,
            InMemoryAdminSessionRepository sessions,
            @Qualifier("adminPasswordEncoder") PasswordEncoder passwordEncoder,
            @Value("${unispeaking.admin.session-idle-seconds:1800}") long idleSeconds,
            @Value("${unispeaking.admin.session-absolute-seconds:28800}") long absoluteSeconds) {
        return new AuthService(
                identities,
                sessions,
                passwordEncoder,
                Clock.systemUTC(),
                idleSeconds,
                absoluteSeconds);
    }
}
