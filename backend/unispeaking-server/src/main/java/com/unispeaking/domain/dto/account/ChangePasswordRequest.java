package com.unispeaking.domain.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank(message = "不能为空")
		@Size(min = 6, max = 72, message = "长度必须为 6 到 72 位")
		String currentPassword,
		@NotBlank(message = "不能为空")
		@Size(min = 6, max = 72, message = "长度必须为 6 到 72 位")
		String newPassword) {
}
