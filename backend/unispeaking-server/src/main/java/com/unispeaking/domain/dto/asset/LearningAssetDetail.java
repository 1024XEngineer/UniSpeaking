package com.unispeaking.domain.dto.asset;

import com.unispeaking.domain.dto.evaluation.DialogueEvaluationResult;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import java.util.List;
import java.util.Objects;

public record LearningAssetDetail(
		String sceneId,
		String title,
		String background,
		String aiRole,
		String userRole,
		String learningGoal,
		List<LearningContentItem> wordList,
		List<LearningContentItem> phraseList,
		List<LearningContentItem> sentenceList,
		String latestSessionId,
		DialogueEvaluationResult dialogueEvaluation,
		DialogueReportResult latestReport,
		List<SessionEvaluationRecord> reportHistory) {

	public LearningAssetDetail {
		wordList = List.copyOf(Objects.requireNonNull(wordList));
		phraseList = List.copyOf(Objects.requireNonNull(phraseList));
		sentenceList = List.copyOf(Objects.requireNonNull(sentenceList));
		reportHistory = List.copyOf(Objects.requireNonNull(reportHistory));
	}
}
