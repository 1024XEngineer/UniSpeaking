package com.unispeaking.service.profile;

import com.unispeaking.domain.dto.profile.ProfileOverviewResponse;

public interface ProfileOverviewService {
	ProfileOverviewResponse getOverview(String userId, String month);
}
