package com.unispeaking.domain.dto.session;

import com.unispeaking.domain.vo.provider.ProviderType;
import jakarta.validation.constraints.NotBlank;

public record StartCustomSceneDialogueRequest(
		@NotBlank String offerSdp,
		ProviderType provider,
		String model,
		String voice,
		Boolean translationEnabled) {
}
