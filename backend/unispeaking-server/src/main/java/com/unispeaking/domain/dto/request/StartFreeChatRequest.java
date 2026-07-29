package com.unispeaking.domain.dto.request;

import com.unispeaking.domain.vo.realtime.ProviderType;
import jakarta.validation.constraints.NotBlank;

public record StartFreeChatRequest(
		@NotBlank String offerSdp,
		ProviderType provider,
		String model,
		String voice,
		Boolean translationEnabled) {
}
