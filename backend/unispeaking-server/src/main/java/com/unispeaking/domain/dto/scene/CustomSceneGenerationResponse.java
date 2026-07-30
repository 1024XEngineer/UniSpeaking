package com.unispeaking.domain.dto.scene;

import java.util.List;

public record CustomSceneGenerationResponse(
		String sceneId,
		String title,
		String background,
		String aiRole,
		String userRole,
		String learningGoal,
		int estimatedMinutes,
		List<LearningContentItem> wordList,
		List<LearningContentItem> phraseList,
		List<LearningContentItem> sentenceList,
		String scenePrompt) {

	public CustomSceneGenerationResponse {
		wordList = wordList == null ? List.of() : List.copyOf(wordList);
		phraseList = phraseList == null ? List.of() : List.copyOf(phraseList);
		sentenceList = sentenceList == null ? List.of() : List.copyOf(sentenceList);
	}
}
