package com.unispeaking.infrastructure.realtime;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.domain.vo.session.SessionPrompt;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.dto.session.StartCommand;
import com.unispeaking.provider.AiProviderRegistry;
import org.springframework.stereotype.Component;

/**
 * Infrastructure operation used internally while establishing realtime SDP.
 */
@Component
public class RealtimeSdpExchange {

	private final AiProviderRegistry providerRegistry;
	public RealtimeSdpExchange(AiProviderRegistry providerRegistry) {
		this.providerRegistry = providerRegistry;
	}

	public RealtimeConnectionResult exchangeSdp(
			ProviderType type, AbstractSceneSession session, SessionPrompt prompt, StartCommand command) {
		return providerRegistry.routeRealtime(
				type,
				command.model(),
				(model, provider) -> {
					RealtimeConnectionResult connection = provider.connect(
							new RealtimeConnectCommand(
									session.getId(),
									command.userId(),
									model,
									command.voice(),
									command.offerSdp()));
					RealtimeFlowLog.info(
							"flow.3.sdp.completed localSessionId={} provider={} model={} credentialExpiresAt={}",
							session.getId(),
							provider.type(),
							model,
							connection.credentialExpiresAt());
					return connection;
				});
	}
}
