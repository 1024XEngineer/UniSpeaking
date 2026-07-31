package com.unispeaking.domain.dto.scene;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.unispeaking.domain.vo.provider.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomSceneRequest(
		String userId,
		@Size(max = 1000, message = "本次偏好不能超过1000个字符")
		String userPreference,
		@JsonAlias("prompt")
		@NotBlank
		@Size(max = 500, message = "场景名称或描述不能超过500个字符")
		String sceneInput,
		String offerSdp,
		ProviderType provider,
		String model,
		String voice,
		Boolean translationEnabled) {
}
