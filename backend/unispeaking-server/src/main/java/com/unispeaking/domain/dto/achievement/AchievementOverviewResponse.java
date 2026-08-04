package com.unispeaking.domain.dto.achievement;

import java.util.List;

public record AchievementOverviewResponse(
		List<AchievementSeriesResponse> series) {

	public AchievementOverviewResponse {
		series = series == null ? List.of() : List.copyOf(series);
	}
}
