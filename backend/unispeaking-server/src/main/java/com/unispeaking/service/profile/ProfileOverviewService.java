package com.unispeaking.service.profile;

import com.unispeaking.domain.dto.profile.ProfileOverviewResponse;
import java.time.YearMonth;
import java.util.UUID;

public interface ProfileOverviewService {

	ProfileOverviewResponse getOverview(UUID userId, YearMonth yearMonth);
}
