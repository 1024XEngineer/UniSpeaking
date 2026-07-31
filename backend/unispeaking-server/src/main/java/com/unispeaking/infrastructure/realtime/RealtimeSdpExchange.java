package com.unispeaking.infrastructure.realtime;

import com.unispeaking.common.logging.RealtimeFlowLog;
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
				type,
				command.model(),
				(model, provider) -> {
					var credential = credentialIssuer.issue(provider.type());
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
