package com.unispeaking.domain.vo.prompt;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.vo.scene.SceneConfig;
import com.unispeaking.domain.vo.scene.SceneType;
import java.util.List;

public record CustomScenePromptContext(
		UserProfile profile,
		SceneConfig sceneConfig,
		SceneType sceneType,
		String sceneInput,
		String userPreference,
		List<LearningContentItem> wordList,
		List<LearningContentItem> phraseList,
		List<LearningContentItem> sentenceList) {
}
