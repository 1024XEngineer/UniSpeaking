package com.unispeaking.service.scene;

import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;

public interface CustomSceneGenerationService {

	CustomSceneDefinition generate(
			String sceneId,
			String userId,
			String sceneInput,
			String currentPreference,
			UserProfile profile);
}
