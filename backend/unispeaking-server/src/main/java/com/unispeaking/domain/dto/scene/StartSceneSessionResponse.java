package com.unispeaking.domain.dto.scene;

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
		String flowId,
		SceneFlowStage currentStage,
		Boolean scoringEnabled,
		String sessionId,
		String localSessionId,
		String providerSessionId,
		String answerSdp,
		Instant credentialExpiresAt,
		String voiceId,
		SessionStatus status,
		String startTime,
		String scenePrompt,
		String systemPrompt) {
}
