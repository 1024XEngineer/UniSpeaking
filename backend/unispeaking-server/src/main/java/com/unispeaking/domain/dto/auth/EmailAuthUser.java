package com.unispeaking.domain.dto.auth;

import java.util.UUID;

public record EmailAuthUser(UUID id, String email) {
}
