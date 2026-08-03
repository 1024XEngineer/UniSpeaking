package com.unispeaking.domain.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
		@NotBlank(message = "不能为空")
		@Size(max = 32, message = "不能超过 32 个字符")
		String nickname) {
}
