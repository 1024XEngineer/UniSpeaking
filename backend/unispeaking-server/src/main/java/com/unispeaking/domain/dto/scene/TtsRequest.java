package com.unispeaking.domain.dto.scene;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TtsRequest(
		@NotBlank
		@Size(max = 500, message = "TTS 文本不能超过500个字符")
		String text,
		String model) {
}
