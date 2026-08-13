package com.unispeaking.domain.po.session;

import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.session.SessionStatus;
import java.time.Instant;
import java.util.UUID;

public record PracticeSessionRecord(
		String sessionId,
		UUID userId,
		String sceneId,
			SceneType sceneType,
			SessionStatus status,
			Instant startedAt,
			Instant endedAt,
			String providerSessionId,
			ProviderType providerType,
			String providerModel,
			String providerTraceId) {
	public PracticeSessionRecord(
			String sessionId,
			UUID userId,
			String sceneId,
			SceneType sceneType,
			SessionStatus status,
			Instant startedAt,
			Instant endedAt,
			String providerSessionId) {
		this(sessionId, userId, sceneId, sceneType, status, startedAt, endedAt,
				providerSessionId, null, null, null);
	}

	public PracticeSessionRecord(
			String sessionId,
			UUID userId,
			String sceneId,
			SceneType sceneType,
			SessionStatus status,
			Instant startedAt,
			Instant endedAt) {
		this(sessionId, userId, sceneId, sceneType, status, startedAt, endedAt,
				null, null, null, null);
	}
}
