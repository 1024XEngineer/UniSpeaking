package com.unispeaking.domain.vo.prompt;

import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.vo.scene.SceneConfig;

public record FreeChatPromptContext(
		UserProfile profile,
		SceneConfig sceneConfig,
		String topic,
		String userPreference) {

	public FreeChatPromptContext(UserProfile profile, SceneConfig sceneConfig, String topic) {
		this(profile, sceneConfig, topic, null);
	}
}
