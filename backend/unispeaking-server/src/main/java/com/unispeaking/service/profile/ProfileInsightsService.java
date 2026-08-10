package com.unispeaking.service.profile;

import com.unispeaking.domain.dto.profile.ProfileInsightsResponse;
import com.unispeaking.domain.dto.profile.UpdateWeeklyLearningGoalsRequest;

public interface ProfileInsightsService {
	/** Returns learning metrics and recommendations for the user. */
	ProfileInsightsResponse getInsights(String userId);

	/** Updates weekly goals and returns refreshed learning insights. */
	ProfileInsightsResponse updateGoals(
			String userId,
			UpdateWeeklyLearningGoalsRequest request);
}
