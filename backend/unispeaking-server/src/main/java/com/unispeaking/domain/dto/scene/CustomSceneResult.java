package com.unispeaking.domain.dto.scene;

import java.util.List;

public record CustomSceneResult(
		String sceneId,
		List<LearningContentItem> wordList,
		List<LearningContentItem> phraseList,
		List<LearningContentItem> sentenceList,
		String dialoguePrompt) {

	public CustomSceneResult {
		wordList = wordList == null ? List.of() : List.copyOf(wordList);
		phraseList = phraseList == null ? List.of() : List.copyOf(phraseList);
		sentenceList = sentenceList == null ? List.of() : List.copyOf(sentenceList);
	}
}
