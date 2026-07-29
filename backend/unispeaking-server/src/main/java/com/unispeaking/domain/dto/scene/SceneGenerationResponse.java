package com.unispeaking.domain.dto.scene;

import java.util.List;

public record SceneGenerationResponse(
		String sceneId,
		List<LearningContentItem> wordList,
		List<LearningContentItem> phraseList,
		List<LearningContentItem> sentenceList,
		String scenePrompt) {

	public SceneGenerationResponse {
		wordList = wordList == null ? List.of() : List.copyOf(wordList);
		phraseList = phraseList == null ? List.of() : List.copyOf(phraseList);
		sentenceList = sentenceList == null ? List.of() : List.copyOf(sentenceList);
		scenePrompt = scenePrompt == null ? "" : scenePrompt;
	}
}
