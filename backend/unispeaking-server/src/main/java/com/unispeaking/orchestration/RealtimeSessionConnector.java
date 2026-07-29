package com.unispeaking.orchestration;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.domain.dto.command.StartCommand;
import com.unispeaking.domain.dto.scene.StartSceneSessionRequest;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.prompt.SessionPrompt;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.realtime.RealtimeConnectionResult;
import com.unispeaking.exception.SessionNotFoundException;
import com.unispeaking.repository.SessionStateStore;
import com.unispeaking.service.quota.UsageQuotaService;
import com.unispeaking.service.realtime.RealtimeConnectionService;
import org.springframework.stereotype.Component;

@Component
public class RealtimeSessionConnector {

	private final SessionStateStore sessionStateStore;
	private final RealtimeConnectionService realtimeConnectionService;
	private final UsageQuotaService usageQuotaService;

	public RealtimeSessionConnector(
			SessionStateStore sessionStateStore,
			RealtimeConnectionService realtimeConnectionService,
			UsageQuotaService usageQuotaService) {
		this.sessionStateStore = sessionStateStore;
		this.realtimeConnectionService = realtimeConnectionService;
		this.usageQuotaService = usageQuotaService;
	}

	public RealtimeSessionConnection connect(
			String sessionId,
			String sceneId,
			String prompt,
			StartSceneSessionRequest request) {
		AbstractSceneSession session = findSession(sessionId);
		ProviderType providerType = request.provider() == null ? ProviderType.QWEN : request.provider();
		String voiceId = request.voice() == null || request.voice().isBlank()
				? "Katerina"
				: request.voice().trim();
		session.setSceneId(sceneId);
		session.setSceneType(request.sceneType());
		session.setProviderType(providerType);
		session.setModel(request.model());
		session.setVoiceId(voiceId);
		session.setPrompt(new SessionPrompt(prompt));
		session.markConnecting();
		sessionStateStore.save(session);

		StartCommand command = new StartCommand(
				request.sceneType(),
				session.getUserId(),
				sceneId,
				request.offerSdp(),
				prompt,
				providerType,
				request.model(),
				voiceId,
				request.translationEnabled());
		try {
			RealtimeConnectionResult connection = realtimeConnectionService.connect(
					providerType,
					session,
					session.getPrompt(),
					command);
			if (connection.providerSessionId() != null
					&& !connection.providerSessionId().isBlank()) {
				session.bindProviderSession(connection.providerSessionId());
			}
			session.setCredentialExpiresAt(connection.credentialExpiresAt());
			session.waitForClient();
			sessionStateStore.save(session);
			RealtimeFlowLog.info(
					"session.realtime.connected sessionId={} provider={} model={} voice={} status={}",
					session.getId(),
					providerType,
					request.model(),
					voiceId,
					session.getStatus());
			return new RealtimeSessionConnection(
					session.getProviderSessionId(),
					connection.answerSdp(),
					connection.credentialExpiresAt(),
					voiceId,
					session.getStatus());
		}
		catch (RuntimeException exception) {
			session.fail("REALTIME_CONNECTION_FAILED", exception.getMessage());
			sessionStateStore.save(session);
			usageQuotaService.settle(session.getId());
			throw exception;
		}
	}

	private AbstractSceneSession findSession(String sessionId) {
		return sessionStateStore.findById(sessionId)
				.orElseThrow(() -> new SessionNotFoundException(sessionId));
	}
}
