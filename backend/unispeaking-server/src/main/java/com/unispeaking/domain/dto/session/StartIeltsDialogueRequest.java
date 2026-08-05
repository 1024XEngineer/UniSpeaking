package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.vo.provider.ProviderType;
import jakarta.validation.constraints.NotBlank;

public record StartIeltsDialogueRequest(
		@NotBlank String offerSdp,
		ProviderType provider,
		String model,
		@NotBlank String voiceId,
		Boolean translationEnabled) {
}
