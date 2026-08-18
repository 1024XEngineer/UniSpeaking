package com.unispeaking.provider.config;

import com.unispeaking.domain.vo.provider.AiCapability;
import java.math.BigDecimal;

public record AiModelConfiguration(
		String modelId,
		String providerId,
		String displayName,
		AiCapability capability,
		boolean enabled,
		String billingUnit,
		BigDecimal inputPricePerMillion,
		BigDecimal outputPricePerMillion,
		BigDecimal characterPricePerMillion,
		BigDecimal audioInputPricePerMinute,
		BigDecimal audioOutputPricePerMinute,
		BigDecimal requestPricePerCall,
		String currency) {
}
