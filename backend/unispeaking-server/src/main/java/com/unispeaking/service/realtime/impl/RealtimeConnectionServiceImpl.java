package com.unispeaking.service.realtime.impl;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.domain.vo.prompt.SessionPrompt;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.realtime.RealtimeConnectionResult;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.dto.command.StartCommand;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.service.realtime.RealtimeConnectionService;
import com.unispeaking.service.realtime.RealtimeCredentialService;
import org.springframework.stereotype.Service;

@Service
public class RealtimeConnectionServiceImpl implements RealtimeConnectionService {

	private final AiProviderRegistry providerRegistry;
	private final RealtimeCredentialService credentialService;

	public RealtimeConnectionServiceImpl(
			AiProviderRegistry providerRegistry,
			RealtimeCredentialService credentialService) {
		this.providerRegistry = providerRegistry;
		this.credentialService = credentialService;
	}

	@Override
	public RealtimeConnectionResult connect(
			ProviderType type, AbstractSceneSession session, SessionPrompt prompt, StartCommand command) {
		RealtimeAttempt attempt = providerRegistry.routeRealtime(
				type,
				command.model(),
				(model, provider) -> {
					var credential = credentialService.getCredential(provider.type());
					String answerSdp = provider.exchangeRealtimeSdp(
							model,
							command.offerSdp(),
							credential.bearerToken());
					RealtimeFlowLog.info(
							"flow.3.sdp.completed localSessionId={} provider={} model={} credentialExpiresAt={}",
							session.getId(),
							provider.type(),
							model,
							credential.expiresAt());
					return new RealtimeAttempt(answerSdp, credential.expiresAt());
				});
		return new RealtimeConnectionResult(
				null,
				attempt.answerSdp(),
				attempt.credentialExpiresAt());
	}

	private record RealtimeAttempt(
			String answerSdp,
			java.time.Instant credentialExpiresAt) {
	}
}
