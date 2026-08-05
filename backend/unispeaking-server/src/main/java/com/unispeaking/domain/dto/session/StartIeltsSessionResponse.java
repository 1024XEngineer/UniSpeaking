package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.SessionStatus;
import java.time.Instant;

public record StartIeltsSessionResponse(
		String sceneId,
		String sceneName,
		SceneType sceneType,
		IeltsContent content,
		IeltsPart currentStage,
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
