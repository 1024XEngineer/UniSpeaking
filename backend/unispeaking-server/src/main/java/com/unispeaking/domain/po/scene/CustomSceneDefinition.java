package com.unispeaking.domain.po.scene;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import java.util.List;

public record CustomSceneDefinition(
		String sceneId,
		String userId,
		String title,
		String background,
		String aiRole,
		String userRole,
		String learningGoal,
		String customInstruction,
		String successFactorJson,
		List<LearningContentItem> wordList,
		List<LearningContentItem> phraseList,
		List<LearningContentItem> sentenceList) {

	public CustomSceneDefinition {
		wordList = wordList == null ? List.of() : List.copyOf(wordList);
		phraseList = phraseList == null ? List.of() : List.copyOf(phraseList);
		sentenceList = sentenceList == null ? List.of() : List.copyOf(sentenceList);
	}
}
