package com.unispeaking.component.session;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.common.exception.SessionNotFoundException;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.StartCommand;
import com.unispeaking.domain.dto.session.StartIeltsSessionResponse;
import com.unispeaking.domain.dto.session.StartSceneSessionResponse;
import com.unispeaking.domain.dto.session.StartSessionResponse;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.scene.IeltsContent;
import com.unispeaking.domain.vo.scene.IeltsPart;
import com.unispeaking.domain.vo.scene.SceneFlowStage;
import com.unispeaking.domain.vo.scene.SceneType;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.domain.vo.session.SessionPrompt;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.infrastructure.realtime.RealtimeSdpExchange;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RealtimeSessionCoordinator {

	private final ActiveSessionRegistry sessions;
	private final PracticeSessionRepository practiceSessions;
	private final RealtimeSdpExchange realtimeSdpExchange;

	public RealtimeSessionCoordinator(
			ActiveSessionRegistry sessions,
			PracticeSessionRepository practiceSessions,
			RealtimeSdpExchange realtimeSdpExchange) {
		this.sessions = sessions;
		this.practiceSessions = practiceSessions;
		this.realtimeSdpExchange = realtimeSdpExchange;
	}

	public StartSceneSessionResponse connect(
			SceneGenerationResponse scene,
			String sceneName,
			SceneFlowStage stage,
			boolean scoringEnabled,
			StartSessionResponse started,
			SceneType sceneType,
			String sceneId,
			String prompt,
			String offerSdp,
			ProviderType provider,
			String model,
			String voice,
			Boolean translationEnabled) {
		AbstractSceneSession session = sessions.findById(started.sessionId())
				.orElseThrow(() -> new SessionNotFoundException(started.sessionId()));
		ProviderType providerType = provider == null ? ProviderType.QWEN : provider;
		String voiceId = voice == null || voice.isBlank() ? "Katerina" : voice.trim();
		session.setSceneId(sceneId);
		session.setSceneType(sceneType);
		session.setProviderType(providerType);
		session.setModel(model);
		session.setVoiceId(voiceId);
		session.setPrompt(new SessionPrompt(prompt));
		session.markConnecting();
		sessions.save(session);
		StartCommand command = new StartCommand(
				sceneType,
				session.getUserId(),
				sceneId,
				offerSdp,
				prompt,
				providerType,
				model,
				voiceId,
				translationEnabled);
		try {
			RealtimeConnectionResult connection = realtimeSdpExchange.exchangeSdp(
					providerType,
					session,
					session.getPrompt(),
					command);
			if (connection.providerSessionId() != null
					&& !connection.providerSessionId().isBlank()) {
				session.bindProviderSession(connection.providerSessionId());
				practiceSessions.bindProviderSession(
						started.sessionId(),
						UUID.fromString(session.getUserId()),
						connection.providerSessionId());
			}
			session.setCredentialExpiresAt(connection.credentialExpiresAt());
			session.waitForClient();
			sessions.save(session);
			return new StartSceneSessionResponse(
					scene.sceneId(),
					sceneName,
					session.getSceneType(),
					scene.wordList(),
					scene.phraseList(),
					scene.sentenceList(),
					stage,
					scoringEnabled,
					started.sessionId(),
					session.getProviderSessionId(),
					connection.answerSdp(),
					connection.credentialExpiresAt(),
					session.getVoiceId(),
					session.getStatus(),
					started.startTime(),
					session.getPrompt().systemPrompt());
		}
		catch (RuntimeException exception) {
			session.fail("REALTIME_CONNECTION_FAILED", exception.getMessage());
			practiceSessions.fail(
					started.sessionId(),
					UUID.fromString(session.getUserId()),
					session.getEndedAt());
			sessions.remove(started.sessionId());
			throw exception;
		}
	}

	public StartIeltsSessionResponse connectIelts(
			IeltsContent content,
			IeltsPart activePart,
			String sceneName,
			SceneFlowStage stage,
			boolean scoringEnabled,
			StartSessionResponse started,
			String sceneId,
			String prompt,
			String offerSdp,
			ProviderType provider,
			String model,
			String voice,
			Boolean translationEnabled) {
		AbstractSceneSession ieltsSession = sessions.findById(started.sessionId())
				.orElseThrow(() -> new SessionNotFoundException(
						started.sessionId()));
		ieltsSession.setIeltsPart(activePart);
		sessions.save(ieltsSession);
		StartSceneSessionResponse connected = connect(
				new SceneGenerationResponse(
						sceneId,
						java.util.List.of(),
						java.util.List.of(),
						java.util.List.of(),
						prompt),
				sceneName,
				stage,
				scoringEnabled,
				started,
				SceneType.IELTS_SCENE,
				sceneId,
				prompt,
				offerSdp,
				provider,
				model,
				voice,
				translationEnabled);
		return new StartIeltsSessionResponse(
				connected.sceneId(),
				connected.sceneName(),
				connected.sceneType(),
				content,
				activePart,
				connected.scoringEnabled(),
				connected.sessionId(),
				connected.providerSessionId(),
				connected.answerSdp(),
				connected.credentialExpiresAt(),
				connected.voiceId(),
				connected.status(),
				connected.startTime(),
				connected.systemPrompt());
	}

	public AbstractSceneSession requireOwnedSession(
			String userId,
			String sessionId) {
		if (userId == null || userId.isBlank()) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "请先登录");
		}
		AbstractSceneSession session = sessions.findById(sessionId)
				.orElseThrow(() -> new SessionNotFoundException(sessionId));
		if (!userId.equals(session.getUserId())) {
			throw new BusinessException(
					"SESSION_ACCESS_DENIED",
					"当前用户无权访问该会话");
		}
		return session;
	}

	public void remove(String sessionId) {
		sessions.remove(sessionId);
	}
}
