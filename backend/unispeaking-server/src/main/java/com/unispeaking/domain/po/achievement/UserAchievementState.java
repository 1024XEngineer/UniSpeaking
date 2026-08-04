package com.unispeaking.domain.po.achievement;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserAchievementState(UUID userId, Instant initializedAt) {

	public UserAchievementState {
		Objects.requireNonNull(userId, "userId must not be null");
		Objects.requireNonNull(initializedAt, "initializedAt must not be null");
	}
}
