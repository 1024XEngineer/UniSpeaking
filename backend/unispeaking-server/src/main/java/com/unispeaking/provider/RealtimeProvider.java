package com.unispeaking.provider;

import com.unispeaking.domain.vo.ai.AiCapability;
import com.unispeaking.domain.vo.realtime.ProviderType;
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
}
