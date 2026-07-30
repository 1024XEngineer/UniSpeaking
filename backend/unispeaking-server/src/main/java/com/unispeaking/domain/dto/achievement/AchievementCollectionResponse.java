package com.unispeaking.domain.dto.achievement;

import java.util.List;

public record AchievementCollectionResponse(
		long unlockedCount,
		long totalCount,
		List<AchievementItemResponse> items) {

	public AchievementCollectionResponse {
		items = List.copyOf(items);
	}

	public static AchievementCollectionResponse empty() {
		return new AchievementCollectionResponse(0, 0, List.of());
	}
}
