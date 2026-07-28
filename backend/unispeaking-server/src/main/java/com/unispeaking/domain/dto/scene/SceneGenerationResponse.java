package com.unispeaking.domain.dto.scene;

import com.unispeaking.domain.vo.scene.SceneType;
import java.util.List;

public record SceneGenerationResponse(
		String sceneId,
		String sceneName,
		SceneType sceneType,
		List<LearningContentItem> wordList,
		List<LearningContentItem> phraseList,
		List<LearningContentItem> sentenceList,
		String scenePrompt) {
}
