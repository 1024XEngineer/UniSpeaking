package com.unispeaking.admin.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminAccountTest {
    @Test
    void disabledAccountCannotAuthenticate() {
        var account = new AdminAccount(
                UUID.randomUUID(),
                "admin@unispeaking.local",
                "hash",
                AdminRole.SUPER_ADMIN,
                false);

        assertThat(account.canAuthenticate()).isFalse();
    }
}
