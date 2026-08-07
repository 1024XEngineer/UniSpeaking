package com.unispeaking.service.profile;

import com.unispeaking.domain.dto.profile.ProfileOverviewResponse;

public interface ProfileOverviewService {
	/** Returns the user's profile overview for the requested month. */
	ProfileOverviewResponse getOverview(String userId, String month);
}
