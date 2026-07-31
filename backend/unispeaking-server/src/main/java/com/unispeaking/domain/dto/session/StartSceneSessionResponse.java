package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import java.time.Instant;
import java.util.List;

public record StartSceneSessionResponse(
		String sceneId,
		String sceneName,
		SceneType sceneType,
		List<LearningContentItem> wordList,
		List<LearningContentItem> phraseList,
		List<LearningContentItem> sentenceList,
		SceneFlowStage currentStage,
		Boolean scoringEnabled,
		String sessionId,
		String providerSessionId,
		String answerSdp,
		Instant credentialExpiresAt,
		String voiceId,
		SessionStatus status,
		String startTime,
		String systemPrompt) {
}
