package com.unispeaking.admin.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unispeaking.admin.auth.adapters.memory.InMemoryAdminIdentityRepository;
import com.unispeaking.admin.auth.adapters.memory.InMemoryAdminSessionRepository;
import com.unispeaking.admin.auth.domain.AdminAccount;
import com.unispeaking.admin.auth.domain.AdminRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

class AuthServiceTest {
    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final InMemoryAdminIdentityRepository identities = new InMemoryAdminIdentityRepository();
    private final InMemoryAdminSessionRepository sessions = new InMemoryAdminSessionRepository();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-20T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsOpaqueSessionForEnabledAdministrator() {
        identities.save(new AdminAccount(
                UUID.randomUUID(),
                "admin@unispeaking.local",
                encoder.encode("correct horse battery staple"),
                AdminRole.SUPER_ADMIN,
                true));

        var result = new AuthService(identities, sessions, encoder, clock, 1800, 28800)
                .login("admin@unispeaking.local", "correct horse battery staple");

        assertThat(result.rawToken()).isNotBlank();
        assertThat(sessions.size()).isEqualTo(1);
        assertThat(sessions.findByTokenHash(AuthService.hash(result.rawToken()))).isPresent();
    }

    @Test
    void returnsSameGenericFailureForWrongPasswordAndDisabledAccount() {
        identities.save(new AdminAccount(
                UUID.randomUUID(),
                "disabled@unispeaking.local",
                encoder.encode("correct horse battery staple"),
                AdminRole.AUDITOR,
                false));
        var service = new AuthService(identities, sessions, encoder, clock, 1800, 28800);

        assertThatThrownBy(() -> service.login("missing@unispeaking.local", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.login("disabled@unispeaking.local", "correct horse battery staple"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
