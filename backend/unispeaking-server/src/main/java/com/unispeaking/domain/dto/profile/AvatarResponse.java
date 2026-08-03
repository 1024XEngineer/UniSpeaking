package com.unispeaking.domain.dto.profile;

import java.time.Instant;

public record AvatarResponse(String avatarUrl, Instant avatarUrlExpiresAt) {
}
