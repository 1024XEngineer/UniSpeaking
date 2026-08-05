package com.unispeaking.service.profile;

import com.unispeaking.domain.dto.profile.ProfileInsightsResponse;
import com.unispeaking.domain.dto.profile.UpdateWeeklyLearningGoalsRequest;

public interface ProfileInsightsService {
	ProfileInsightsResponse getInsights(String userId);
	ProfileInsightsResponse updateGoals(
			String userId,
			UpdateWeeklyLearningGoalsRequest request);
}
