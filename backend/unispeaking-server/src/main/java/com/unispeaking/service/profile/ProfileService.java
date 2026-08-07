package com.unispeaking.service.profile;

import com.unispeaking.domain.dto.profile.UpdateUserPreferenceRequest;
import com.unispeaking.domain.dto.profile.UserPreferenceResponse;
import com.unispeaking.domain.po.profile.UserProfile;

public interface ProfileService {
	/** Returns the user's basic profile. */
	UserProfile getProfile(String userId);

	/** Returns the user's training preferences. */
	UserPreferenceResponse getPreference(String userId);

	/** Updates and returns the user's training preferences. */
	UserPreferenceResponse updatePreference(String userId, UpdateUserPreferenceRequest request);
}
