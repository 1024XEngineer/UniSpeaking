package com.unispeaking.service.profile.query;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

public interface LearningAssetCountPort {

	long countSavedAssets(UUID userId);

	Map<LocalDate, Long> countSavedAssetsByDate(
			UUID userId,
			YearMonth yearMonth,
			ZoneId zoneId);
}
