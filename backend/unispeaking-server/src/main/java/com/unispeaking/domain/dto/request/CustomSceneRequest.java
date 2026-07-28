package com.unispeaking.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.unispeaking.domain.vo.realtime.ProviderType;
import jakarta.validation.constraints.NotBlank;

public record CustomSceneRequest(
		String userId,
		String userPreference,
		@JsonAlias("prompt") @NotBlank String sceneInput,
		String offerSdp,
		ProviderType provider,
		String model,
		String voice,
		Boolean translationEnabled) {
}
