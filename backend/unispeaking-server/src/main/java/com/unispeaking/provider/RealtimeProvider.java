package com.unispeaking.provider;

import com.unispeaking.domain.dto.ai.RealtimeSdpExchangeRequest;
import com.unispeaking.domain.dto.ai.RealtimeSdpExchangeResponse;
import com.unispeaking.domain.vo.ai.AiCapability;
import com.unispeaking.domain.vo.realtime.ProviderType;
import java.util.Set;

public abstract class RealtimeProvider extends AbstractAiProvider {

	protected RealtimeProvider(ProviderType providerType, Set<String> supportedModels) {
		super(providerType, supportedModels);
	}

	@Override
	public final AiCapability capability() {
		return AiCapability.REALTIME;
	}

	public abstract RealtimeSdpExchangeResponse exchangeRealtimeSdp(
			RealtimeSdpExchangeRequest request);
}
