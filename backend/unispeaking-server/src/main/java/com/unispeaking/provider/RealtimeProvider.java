package com.unispeaking.provider;

import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
import com.unispeaking.domain.vo.session.RealtimeCredential;
import java.util.Set;

public abstract class RealtimeProvider extends AbstractAiProvider {

	private final ProviderType providerType;

	protected RealtimeProvider(ProviderType providerType, Set<String> supportedModels) {
		super(providerType == null ? null : providerType.name(), supportedModels);
		this.providerType = providerType;
	}

	public final ProviderType type() {
		return providerType;
	}

	@Override
	public final AiCapability capability() {
		return AiCapability.REALTIME;
	}

	@Override
	public final String exchangeRealtimeSdp(String offerSdp, String token) {
		return exchangeRealtimeSdp(null, offerSdp, token);
	}

	/**
	 * Exchanges a WebRTC Offer SDP for the provider's Answer SDP.
	 */
	public abstract String exchangeRealtimeSdp(
			String modelId,
			String offerSdp,
			String token);

	public boolean requiresIssuedCredential() {
		return true;
	}

	/**
	 * Establishes a provider realtime session. Providers with a control-plane
	 * session lifecycle override this method and return their external session ID.
	 */
	public RealtimeConnectionResult connect(
			RealtimeConnectCommand command,
			RealtimeCredential credential) {
		String answerSdp = exchangeRealtimeSdp(
				command.modelId(),
				command.offerSdp(),
				credential.bearerToken());
		return new RealtimeConnectionResult(
				null,
				type(),
				command.modelId(),
				command.voiceId(),
				null,
				answerSdp,
				credential.expiresAt());
	}

	/**
	 * Stops a provider-owned realtime session. Direct SDP providers have no
	 * separate control-plane session and keep the default no-op behavior.
	 */
	public void stopSession(String providerSessionId, String token, String reason) {
		// No provider control-plane session to stop.
	}
}
