package com.unispeaking.domain.dto.ai;

import com.unispeaking.domain.vo.ai.AiCallContext;

public record RealtimeSdpExchangeRequest(
		AiCallContext context,
		String model,
		String offerSdp,
		String apiKey) {
}
