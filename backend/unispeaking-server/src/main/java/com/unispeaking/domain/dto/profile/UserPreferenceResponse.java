package com.unispeaking.domain.dto.profile;

import com.unispeaking.domain.po.profile.UserProfile;

public record UserPreferenceResponse(
		String userId,
		String preferredVoice,
		String preferredAiSpeechSpeed,
		String cefrLevel,
		String memoryText) {

	public static UserPreferenceResponse from(UserProfile profile) {
		return new UserPreferenceResponse(
				profile.userId(),
				profile.voiceId(),
				profile.aiSpeechSpeed(),
				profile.level(),
				profile.memoryText());
	}
}
