package com.unispeaking.service.profile.impl;

import com.unispeaking.domain.dto.profile.UpdateUserPreferenceRequest;
import com.unispeaking.domain.dto.profile.UserPreferenceResponse;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.repository.UserProfileRepository;
import com.unispeaking.service.profile.ProfileService;
import org.springframework.stereotype.Service;

@Service
public class ProfileServiceImpl implements ProfileService {

	private final UserProfileRepository repository;

	public ProfileServiceImpl(UserProfileRepository repository) {
		this.repository = repository;
	}

	@Override
	public UserProfile getProfile(String userId) {
		return repository.findByUserId(userId)
				.orElseGet(() -> repository.save(
						new UserProfile(userId, null, null, "NATURAL", "zh-CN", "")));
	}

	@Override
	public UserPreferenceResponse getPreference(String userId) {
		return UserPreferenceResponse.from(getProfile(userId));
	}

	@Override
	public UserPreferenceResponse updatePreference(
			String userId,
			UpdateUserPreferenceRequest request) {
		UserProfile profile = getProfile(userId);
		UserProfile updated = profile.withPreferences(
				request.preferredVoice() == null ? null : request.preferredVoice().name(),
				request.preferredAiSpeechSpeed() == null
						? null
						: request.preferredAiSpeechSpeed().name(),
				request.cefrLevel() == null ? null : request.cefrLevel().name(),
				request.memoryText());
		return UserPreferenceResponse.from(repository.save(updated));
	}
}
