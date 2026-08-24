package com.unispeaking.domain.dto.scene;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyPicksResponse(
		LocalDate date,
		String timezone,
		Instant nextRefreshAt,
		List<DailyPickResponse> picks) {

	public DailyPicksResponse {
		picks = List.copyOf(picks);
	}
}
