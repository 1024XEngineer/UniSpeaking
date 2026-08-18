package com.unispeaking.infrastructure.realtime;

import com.unispeaking.common.logging.RealtimeFlowLog;
import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.domain.dto.session.StartCommand;
import com.unispeaking.domain.po.session.AbstractSceneSession;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.domain.vo.session.RealtimeCredential;
import com.unispeaking.domain.vo.session.SessionPrompt;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.AiInvocationContext;
import org.springframework.stereotype.Component;

/**
 * Infrastructure operation used internally while establishing realtime SDP.
 */
@Component
public class RealtimeSdpExchange {

	private final AiProviderRegistry providerRegistry;
	private final RealtimeCredentialIssuer credentialIssuer;

	public RealtimeSdpExchange(
			AiProviderRegistry providerRegistry,
			RealtimeCredentialIssuer credentialIssuer) {
		this.providerRegistry = providerRegistry;
		this.credentialIssuer = credentialIssuer;
	}

	public RealtimeConnectionResult exchangeSdp(
			ProviderType type, AbstractSceneSession session, SessionPrompt prompt, StartCommand command) {
		RealtimeAttempt attempt = providerRegistry.routeRealtime(
				AiInvocationContext.create(command.userId(), session.getId(), "realtime_connect"),
				type,
				command.model(),
				(model, provider) -> {
					RealtimeCredential credential = provider.requiresIssuedCredential()
							? credentialIssuer.issue(provider.type())
							: new RealtimeCredential("", null);
					RealtimeConnectionResult connection = provider.connect(
							new RealtimeConnectCommand(
									model,
									command.offerSdp(),
									command.userId(),
									session.getId(),
									command.sceneId(),
									command.sceneType(),
									command.voice()),
							credential);
					RealtimeFlowLog.info(
							"flow.3.sdp.completed localSessionId={} provider={} model={} credentialExpiresAt={}",
							session.getId(),
							connection.providerType(),
							connection.modelId(),
							connection.credentialExpiresAt());
					return new RealtimeAttempt(connection);
				});
		return attempt.connection();
	}

	private record RealtimeAttempt(
			RealtimeConnectionResult connection) {
	}
}
