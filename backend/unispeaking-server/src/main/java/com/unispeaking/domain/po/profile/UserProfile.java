package com.unispeaking.domain.po.profile;

public record UserProfile(
		String userId,
		String level,
		String voiceId,
		String nativeLanguage,
		String memory) {

	public UserProfile {
		memory = memory == null ? "" : memory;
	}

	public UserProfile withMemory(String updatedMemory) {
		return new UserProfile(userId, level, voiceId, nativeLanguage, updatedMemory);
	}
}
