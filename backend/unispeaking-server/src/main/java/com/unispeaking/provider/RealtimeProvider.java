package com.unispeaking.provider;

import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.provider.ProviderType;
import com.unispeaking.domain.dto.session.RealtimeConnectCommand;
import com.unispeaking.domain.vo.session.RealtimeConnectionResult;
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

	/**
	 * Establishes the provider session and exchanges the WebRTC SDP offer.
	 */
	public abstract RealtimeConnectionResult connect(RealtimeConnectCommand command);

	/**
	 * Stops a provider-owned session. Providers without a separate control-plane
	 * session can keep the default no-op behavior.
	 */
	public void stop(String providerSessionId) {
		// No provider-side session resource to release.
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
}
