package com.unispeaking.domain.dto.scene;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TranslateTextRequest(
		@NotBlank(message = "待翻译文本不能为空")
		@Size(max = 4000, message = "待翻译文本不能超过4000个字符")
		String text) {
}
