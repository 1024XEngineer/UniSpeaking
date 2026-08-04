package com.unispeaking.domain.dto.achievement;

import java.util.List;

public record AchievementSyncResponse(
		boolean initialized,
		AchievementOverviewResponse overview,
		List<AchievementNotificationResponse> newlyUnlocked,
		List<AchievementNotificationResponse> pendingNotifications) {

	public AchievementSyncResponse {
		newlyUnlocked = newlyUnlocked == null
				? List.of()
				: List.copyOf(newlyUnlocked);
		pendingNotifications = pendingNotifications == null
				? List.of()
				: List.copyOf(pendingNotifications);
	}
}
