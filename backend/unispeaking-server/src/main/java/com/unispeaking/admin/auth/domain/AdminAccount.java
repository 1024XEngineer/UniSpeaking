package com.unispeaking.admin.auth.domain;

import java.util.UUID;

public record AdminAccount(
        UUID id,
        String login,
        String passwordHash,
        AdminRole role,
        boolean enabled) {

    public boolean canAuthenticate() {
        return enabled;
    }
}
