package com.unispeaking.domain.po.achievement;

import java.util.Objects;

public record AchievementUnlockCreation(
		UserAchievementUnlock unlock,
		boolean created) {

	public AchievementUnlockCreation {
		Objects.requireNonNull(unlock, "unlock must not be null");
	}
}
