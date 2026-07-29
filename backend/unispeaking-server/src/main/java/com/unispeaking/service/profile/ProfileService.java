package com.unispeaking.service.profile;

import com.unispeaking.domain.dto.profile.UpdateUserPreferenceRequest;
import com.unispeaking.domain.dto.profile.UserPreferenceResponse;
import com.unispeaking.domain.po.profile.UserProfile;

public interface ProfileService {
	UserProfile getProfile(String userId);
	UserPreferenceResponse getPreference(String userId);
	UserPreferenceResponse updatePreference(String userId, UpdateUserPreferenceRequest request);
}
