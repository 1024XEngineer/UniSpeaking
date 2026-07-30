package com.unispeaking.domain.po.profile;

public record UserProfile(
		String userId,
		String level,
		String voiceId,
		String aiSpeechSpeed,
		String nativeLanguage,
		String memoryText,
		String preferencesJson) {

	public UserProfile {
		aiSpeechSpeed = aiSpeechSpeed == null || aiSpeechSpeed.isBlank()
				? "NATURAL"
				: aiSpeechSpeed;
		memoryText = memoryText == null ? "" : memoryText.strip();
		preferencesJson = preferencesJson == null || preferencesJson.isBlank()
				? "{}"
				: preferencesJson.strip();
	}

	public UserProfile(
			String userId,
			String level,
			String voiceId,
			String aiSpeechSpeed,
			String nativeLanguage,
			String memoryText) {
		this(
				userId,
				level,
				voiceId,
				aiSpeechSpeed,
				nativeLanguage,
				memoryText,
				"{}");
	}

	public UserProfile(
			String userId,
			String level,
			String voiceId,
			String nativeLanguage,
			String memoryText) {
		this(userId, level, voiceId, "NATURAL", nativeLanguage, memoryText);
	}

	public UserProfile withPreferences(
			String preferredVoice,
			String preferredAiSpeechSpeed,
			String cefrLevel,
			String updatedMemoryText) {
		return new UserProfile(
				userId,
				cefrLevel == null ? level : cefrLevel,
				preferredVoice == null ? voiceId : preferredVoice,
				preferredAiSpeechSpeed == null ? aiSpeechSpeed : preferredAiSpeechSpeed,
				nativeLanguage,
				updatedMemoryText == null ? memoryText : updatedMemoryText,
				preferencesJson);
	}
}
