package com.unispeaking.service.realtime.impl;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.domain.dto.ai.RealtimeSdpExchangeRequest;
import com.unispeaking.domain.vo.ai.AiCapability;
import com.unispeaking.domain.vo.ai.AiCallContext;
import com.unispeaking.domain.vo.prompt.SessionPrompt;
import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.realtime.RealtimeConnectionResult;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.dto.command.StartCommand;
import com.unispeaking.exception.BusinessException;
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
		String model = command.model() == null || command.model().isBlank()
				? providerRegistry.defaultModel(AiCapability.REALTIME)
				: command.model().trim();
		var provider = providerRegistry.getRealtimeProvider(model);
		if (type != null && type != provider.type()) {
			throw new BusinessException(
					"AI_PROVIDER_MODEL_MISMATCH",
					"Requested provider " + type + " does not own realtime model " + model);
		}
		var credential = credentialService.getCredential(provider.type());
		var exchangeResult = provider.exchangeRealtimeSdp(
				new RealtimeSdpExchangeRequest(
						new AiCallContext(session.getUserId(), session.getId()),
						model,
						command.offerSdp(),
						credential.bearerToken()));
		RealtimeFlowLog.info(
				"flow.3.sdp.completed localSessionId={} provider={} model={} credentialExpiresAt={}",
				session.getId(),
				provider.type(),
				model,
				credential.expiresAt());
		return new RealtimeConnectionResult(
				exchangeResult.aiCallId(),
				exchangeResult.answerSdp(),
				credential.expiresAt());
	}
}
